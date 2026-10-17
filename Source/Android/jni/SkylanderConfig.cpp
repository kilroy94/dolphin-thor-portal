// Copyright 2023 Dolphin Emulator Project
// SPDX-License-Identifier: GPL-2.0-or-later

#include <jni.h>

#include <array>

#include "AndroidCommon/AndroidCommon.h"
#include "AndroidCommon/IDCache.h"
#include "Core/IOS/USB/Emulated/Skylanders/Skylander.h"
#include "Core/System.h"
#include "Core/Core.h"
#include "Core/IOS/USB/Emulated/Skylanders/SkylanderCrypto.h"

namespace
{
using Portal = IOS::HLE::USB::SkylanderPortal;
bool PortalGameIsLive()
{
  const auto state = Core::GetState(Core::System::GetInstance());
  return state == Core::State::Running || state == Core::State::Paused;
}

struct CheckedFile
{
  Portal::CommandCode code = Portal::CommandCode::FileError;
  File::IOFile file;
  std::array<u8, IOS::HLE::USB::FIGURE_SIZE> data{};
};

CheckedFile ReadFigure(const std::string& path, const char* mode)
{
  CheckedFile result;
  result.file = File::IOFile(path, mode);
  if (!result.file)
    return result;
  if (result.file.GetSize() != result.data.size() ||
      !result.file.ReadBytes(result.data.data(), result.data.size()))
  {
    result.code = Portal::CommandCode::InvalidFigure;
    return result;
  }
  std::array<u8, 2> checksum{};
  IOS::HLE::USB::SkylanderCrypto::ComputeChecksumType0(result.data.data(), checksum.data());
  if (checksum[0] != result.data[0x1e] || checksum[1] != result.data[0x1f] ||
      (result.data[0] ^ result.data[1] ^ result.data[2] ^ result.data[3]) != result.data[4])
  {
    result.code = Portal::CommandCode::InvalidFigure;
    return result;
  }
  result.code = Portal::CommandCode::Success;
  return result;
}

jobject CommandObject(JNIEnv* env, Portal::CommandResult result)
{
  jclass type = env->FindClass("org/dolphinemu/dolphinemu/features/skylanders/PortalCommandResult");
  return env->NewObject(type, env->GetMethodID(type, "<init>", "(IIJ)V"),
                        static_cast<jint>(result.code), static_cast<jint>(result.slot),
                        static_cast<jlong>(result.instance));
}
}

extern "C" {

JNIEXPORT jobjectArray JNICALL
Java_org_dolphinemu_dolphinemu_features_skylanders_PortalNative_catalogue(JNIEnv* env, jclass)
{
  jclass type = env->FindClass("org/dolphinemu/dolphinemu/features/skylanders/CatalogueEntry");
  jmethodID init = env->GetMethodID(type, "<init>", "(IILjava/lang/String;III)V");
  jobjectArray result = env->NewObjectArray(IOS::HLE::USB::list_skylanders.size(), type, nullptr);
  int index = 0;
  for (const auto& [identity, data] : IOS::HLE::USB::list_skylanders)
  {
    jstring name = ToJString(env, data.name);
    jobject value = env->NewObject(type, init, static_cast<jint>(identity.first),
        static_cast<jint>(identity.second), name, static_cast<jint>(data.game),
        static_cast<jint>(data.type), static_cast<jint>(data.element));
    env->SetObjectArrayElement(result, index++, value);
    env->DeleteLocalRef(value);
    env->DeleteLocalRef(name);
  }
  return result;
}

JNIEXPORT jobject JNICALL
Java_org_dolphinemu_dolphinemu_features_skylanders_SkylanderConfig_getSkylanderMap(JNIEnv* env,
                                                                                   jclass clazz)
{
  jobject hash_map_obj = env->NewObject(IDCache::GetHashMapClass(), IDCache::GetHashMapInit(),
                                        static_cast<u16>(IOS::HLE::USB::list_skylanders.size()));

  jclass skylander_class =
      env->FindClass("org/dolphinemu/dolphinemu/features/skylanders/model/SkylanderPair");

  jmethodID skylander_init = env->GetMethodID(skylander_class, "<init>", "(II)V");

  for (const auto& it : IOS::HLE::USB::list_skylanders)
  {
    const std::string& name = it.second.name;
    jobject skylander_obj =
        env->NewObject(skylander_class, skylander_init, it.first.first, it.first.second);
    env->CallObjectMethod(hash_map_obj, IDCache::GetHashMapPut(), skylander_obj,
                          ToJString(env, name));
    env->DeleteLocalRef(skylander_obj);
  }

  return hash_map_obj;
}

JNIEXPORT jobject JNICALL
Java_org_dolphinemu_dolphinemu_features_skylanders_SkylanderConfig_getInverseSkylanderMap(
    JNIEnv* env, jclass clazz)
{
  jobject hash_map_obj = env->NewObject(IDCache::GetHashMapClass(), IDCache::GetHashMapInit(),
                                        static_cast<u16>(IOS::HLE::USB::list_skylanders.size()));

  jclass skylander_class =
      env->FindClass("org/dolphinemu/dolphinemu/features/skylanders/model/SkylanderPair");

  jmethodID skylander_init = env->GetMethodID(skylander_class, "<init>", "(II)V");

  for (const auto& it : IOS::HLE::USB::list_skylanders)
  {
    const std::string& name = it.second.name;
    jobject skylander_obj =
        env->NewObject(skylander_class, skylander_init, it.first.first, it.first.second);
    env->CallObjectMethod(hash_map_obj, IDCache::GetHashMapPut(), ToJString(env, name),
                          skylander_obj);
    env->DeleteLocalRef(skylander_obj);
  }

  return hash_map_obj;
}

JNIEXPORT jboolean JNICALL
Java_org_dolphinemu_dolphinemu_features_skylanders_SkylanderConfig_removeSkylander(JNIEnv* env,
                                                                                   jclass clazz,
                                                                                   jint slot)
{
  if (slot < 0 || slot >= MAX_SKYLANDERS)
    return false;
  auto& system = Core::System::GetInstance();
  return static_cast<jboolean>(system.GetSkylanderPortal().RemoveSkylander(slot));
}

JNIEXPORT jlong JNICALL
Java_org_dolphinemu_dolphinemu_features_skylanders_PortalNative_beginSession(JNIEnv*, jclass)
{
  return PortalGameIsLive() ? Core::System::GetInstance().GetSkylanderPortal().BeginSession() : 0;
}

JNIEXPORT void JNICALL
Java_org_dolphinemu_dolphinemu_features_skylanders_PortalNative_endSession(JNIEnv*, jclass)
{
  Core::System::GetInstance().GetSkylanderPortal().EndSession();
}

JNIEXPORT jobjectArray JNICALL
Java_org_dolphinemu_dolphinemu_features_skylanders_PortalNative_snapshot(JNIEnv* env, jclass,
                                                                      jlong session)
{
  if (!PortalGameIsLive())
    return nullptr;
  const auto state = Core::System::GetInstance().GetSkylanderPortal().Snapshot(session);
  if (!state)
    return nullptr;
  jclass type = env->FindClass("org/dolphinemu/dolphinemu/features/skylanders/PortalSlotState");
  jmethodID init = env->GetMethodID(type, "<init>", "(IZIIIIJJI)V");
  jobjectArray result = env->NewObjectArray(MAX_SKYLANDERS, type, nullptr);
  for (int i = 0; i < MAX_SKYLANDERS; ++i)
  {
    const auto& s = (*state)[i];
    // Decrypt the copied image after Snapshot releases the USB mutex.
    IOS::HLE::USB::SkylanderFigure detached(File::IOFile{}, true);
    if (s.present)
      for (u8 block = 0; block < IOS::HLE::USB::BLOCK_COUNT; ++block)
        detached.SetBlock(block, s.figure_data.data() + block * IOS::HLE::USB::BLOCK_SIZE);
    jobject value = env->NewObject(type, init, static_cast<jint>(i), static_cast<jboolean>(s.present),
        static_cast<jint>(s.status), static_cast<jint>(s.pending), static_cast<jint>(s.id),
        static_cast<jint>(s.variant), static_cast<jlong>(s.serial), static_cast<jlong>(s.instance), static_cast<jint>(s.present ? detached.GetSavedLevel() : 0));
    env->SetObjectArrayElement(result, i, value);
    env->DeleteLocalRef(value);
  }
  return result;
}

JNIEXPORT jobject JNICALL
Java_org_dolphinemu_dolphinemu_features_skylanders_PortalNative_inspectFile(JNIEnv* env, jclass,
                                                                         jstring path)
{
  auto file = ReadFigure(GetJString(env, path), "rb");
  auto& portal = Core::System::GetInstance().GetSkylanderPortal();
  const auto [id, variant] = portal.CalculateIDs(file.data);
  const u32 serial = static_cast<u32>(file.data[0]) | (static_cast<u32>(file.data[1]) << 8) |
                     (static_cast<u32>(file.data[2]) << 16) | (static_cast<u32>(file.data[3]) << 24);
  auto entry = IOS::HLE::USB::list_skylanders.find({id, variant});
  const std::string name = entry == IOS::HLE::USB::list_skylanders.end() ? "Unknown figure" : entry->second.name;
  int level = 0;
  if (file.code == Portal::CommandCode::Success)
  {
    IOS::HLE::USB::SkylanderFigure figure(std::move(file.file));
    level = figure.GetSavedLevel();
  }
  jclass type = env->FindClass("org/dolphinemu/dolphinemu/features/skylanders/PortalFigureInfo");
  return env->NewObject(type, env->GetMethodID(type, "<init>", "(IIIJLjava/lang/String;I)V"),
      static_cast<jint>(file.code), static_cast<jint>(id), static_cast<jint>(variant),
      static_cast<jlong>(serial), ToJString(env, name), static_cast<jint>(level));
}

JNIEXPORT jobject JNICALL
Java_org_dolphinemu_dolphinemu_features_skylanders_PortalNative_createFile(JNIEnv* env, jclass,
    jint id, jint variant, jstring path)
{
  const auto identity = std::make_pair(static_cast<u16>(id), static_cast<u16>(variant));
  if (id < 0 || id > 65535 || variant < 0 || variant > 65535 ||
      IOS::HLE::USB::list_skylanders.find(identity) == IOS::HLE::USB::list_skylanders.end())
  {
    jclass type = env->FindClass("org/dolphinemu/dolphinemu/features/skylanders/PortalFigureInfo");
    return env->NewObject(type, env->GetMethodID(type, "<init>", "(IIIJLjava/lang/String;I)V"),
        6, id, variant, static_cast<jlong>(0), ToJString(env, "Unknown catalogue entry"), 0);
  }
  if (!IOS::HLE::USB::SkylanderFigure::CreateNew(GetJString(env, path), id, variant))
  {
    jclass type = env->FindClass("org/dolphinemu/dolphinemu/features/skylanders/PortalFigureInfo");
    return env->NewObject(type, env->GetMethodID(type, "<init>", "(IIIJLjava/lang/String;I)V"),
        8, id, variant, static_cast<jlong>(0), ToJString(env, "Cannot create a new copy"), 0);
  }
  return Java_org_dolphinemu_dolphinemu_features_skylanders_PortalNative_inspectFile(env, nullptr, path);
}

JNIEXPORT jobject JNICALL
Java_org_dolphinemu_dolphinemu_features_skylanders_PortalNative_insert(JNIEnv* env, jclass,
    jlong session, jint replace_slot, jlong expected_instance, jstring path)
{
  if (!PortalGameIsLive())
    return CommandObject(env, {Portal::CommandCode::StaleSession});
  if (replace_slot < -1 || replace_slot >= MAX_SKYLANDERS)
    return CommandObject(env, {Portal::CommandCode::InvalidSlot});
  auto checked = ReadFigure(GetJString(env, path), "r+b");
  if (checked.code != Portal::CommandCode::Success)
    return CommandObject(env, {checked.code});
  auto figure = std::make_unique<IOS::HLE::USB::SkylanderFigure>(std::move(checked.file));
  return CommandObject(env, Core::System::GetInstance().GetSkylanderPortal().InsertFigure(
      session, replace_slot, expected_instance, std::move(figure)));
}

JNIEXPORT jobject JNICALL
Java_org_dolphinemu_dolphinemu_features_skylanders_PortalNative_remove(JNIEnv* env, jclass,
    jlong session, jint slot, jlong expected_instance)
{
  if (!PortalGameIsLive())
    return CommandObject(env, {Portal::CommandCode::StaleSession});
  return CommandObject(env, Core::System::GetInstance().GetSkylanderPortal().RemoveFigure(
      session, slot, expected_instance));
}

JNIEXPORT jobject JNICALL
Java_org_dolphinemu_dolphinemu_features_skylanders_SkylanderConfig_loadSkylander(JNIEnv* env,
                                                                                 jclass clazz,
                                                                                 jint slot,
                                                                                 jstring file_name)
{
  if (slot < -1 || slot >= MAX_SKYLANDERS || !file_name)
    return nullptr;
  File::IOFile sky_file(GetJString(env, file_name), "r+b");
  if (!sky_file)
  {
    return nullptr;
  }
  std::array<u8, 0x40 * 0x10> file_data;
  if (!sky_file.ReadBytes(file_data.data(), file_data.size()))
  {
    return nullptr;
  }

  jclass pair_class = env->FindClass("android/util/Pair");
  jmethodID pair_init =
      env->GetMethodID(pair_class, "<init>", "(Ljava/lang/Object;Ljava/lang/Object;)V");
  jclass integer_class = env->FindClass("java/lang/Integer");
  jmethodID int_init = env->GetMethodID(integer_class, "<init>", "(I)V");

  auto& system = Core::System::GetInstance();
  system.GetSkylanderPortal().RemoveSkylander(slot);
  std::string name = "Unknown";

  std::pair<u16, u16> id_var = system.GetSkylanderPortal().CalculateIDs(file_data);

  const auto it = IOS::HLE::USB::list_skylanders.find(std::make_pair(id_var.first, id_var.second));

  if (it != IOS::HLE::USB::list_skylanders.end())
  {
    name = it->second.name;
  }

  return env->NewObject(
      pair_class, pair_init,
      env->NewObject(integer_class, int_init,
                     system.GetSkylanderPortal().LoadSkylander(
                         std::make_unique<IOS::HLE::USB::SkylanderFigure>(std::move(sky_file)))),
      ToJString(env, name));
}

JNIEXPORT jobject JNICALL
Java_org_dolphinemu_dolphinemu_features_skylanders_SkylanderConfig_createSkylander(
    JNIEnv* env, jclass clazz, jint id, jint var, jstring fileName, jint slot)
{
  if (slot < -1 || slot >= MAX_SKYLANDERS || id < 0 || id > 65535 || var < 0 || var > 65535)
    return nullptr;
  u16 sky_id = static_cast<u16>(id);
  u16 sky_var = static_cast<u16>(var);

  std::string file_name = GetJString(env, fileName);

  auto& system = Core::System::GetInstance();
  {
    IOS::HLE::USB::SkylanderFigure figure(file_name);
    figure.Create(sky_id, sky_var);
    figure.Close();
  }
  system.GetSkylanderPortal().RemoveSkylander(slot);

  jclass pair_class = env->FindClass("android/util/Pair");
  jmethodID pair_init =
      env->GetMethodID(pair_class, "<init>", "(Ljava/lang/Object;Ljava/lang/Object;)V");

  jclass integer_class = env->FindClass("java/lang/Integer");
  jmethodID integer_init = env->GetMethodID(integer_class, "<init>", "(I)V");

  File::IOFile sky_file(file_name, "r+b");
  if (!sky_file)
  {
    return nullptr;
  }
  std::array<u8, 0x40 * 0x10> file_data;
  if (!sky_file.ReadBytes(file_data.data(), file_data.size()))
  {
    return nullptr;
  }

  std::string name = "Unknown";

  const auto it = IOS::HLE::USB::list_skylanders.find(std::make_pair(sky_id, sky_var));

  if (it != IOS::HLE::USB::list_skylanders.end())
  {
    name = it->second.name;
  }

  return env->NewObject(
      pair_class, pair_init,
      env->NewObject(integer_class, integer_init,
                     system.GetSkylanderPortal().LoadSkylander(
                         std::make_unique<IOS::HLE::USB::SkylanderFigure>(std::move(sky_file)))),
      ToJString(env, name));
}
}
