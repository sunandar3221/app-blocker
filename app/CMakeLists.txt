cmake_minimum_required(VERSION 3.22.1)

project("appblocker")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(src/main/cpp)

add_library(
    appblocker
    SHARED
    src/main/cpp/sha256.cpp
    src/main/cpp/kiosk_core.cpp
    src/main/cpp/app_blocker.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    appblocker
    ${log-lib}
)
