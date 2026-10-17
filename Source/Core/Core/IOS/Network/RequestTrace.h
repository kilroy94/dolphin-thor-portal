// Copyright 2026 Dolphin Emulator Project
// SPDX-License-Identifier: GPL-2.0-or-later

#pragma once

#include <chrono>
#include <string_view>

#include "Common/CommonTypes.h"
#include "Common/Logging/Log.h"

namespace IOS::HLE
{
// Targeted boot diagnostics. Uses the existing IOS - Network Info log switch; no payloads or keys.
// Request addresses correlate dispatch, worker execution, and asynchronous socket completion.
class NetworkRequestTrace
{
public:
  NetworkRequestTrace(std::string_view stage, u32 address, u32 operation)
      : m_stage(stage), m_address(address), m_operation(operation)
  {
    INFO_LOG_FMT(IOS_NET, "WiiNet begin {} ipc={:08x} op={:x}", stage, address, operation);
  }

  ~NetworkRequestTrace()
  {
    const auto us = std::chrono::duration_cast<std::chrono::microseconds>(
                        std::chrono::steady_clock::now() - m_start)
                        .count();
    INFO_LOG_FMT(IOS_NET, "WiiNet end {} ipc={:08x} op={:x} elapsed_us={}", m_stage, m_address,
                 m_operation, us);
  }

private:
  const std::string_view m_stage;
  const u32 m_address;
  const u32 m_operation;
  const std::chrono::steady_clock::time_point m_start = std::chrono::steady_clock::now();
};
}  // namespace IOS::HLE
