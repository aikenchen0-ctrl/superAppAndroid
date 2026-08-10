cmake_minimum_required(VERSION 3.22.1)

project("adbcore")

set(CMAKE_CXX_STANDARD 17)

add_compile_options(-Werror=format -fdata-sections -ffunction-sections -fno-exceptions -fno-rtti -fno-threadsafe-statics)

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_compile_options(-Os -flto -fvisibility=hidden -fvisibility-inlines-hidden)
    add_link_options(-flto -Wl,--exclude-libs,ALL -Wl,--gc-sections -Wl,--strip-all)
else ()
    add_definitions(-DDEBUG)
endif ()

find_package(boringssl REQUIRED CONFIG)
find_package(cxx REQUIRED CONFIG)

# ----- libadbcore.so -----
# An ELF executable masquerading as a .so so the Android packager preserves
# the executable bit at install time. Launches the server (app_process) in
# the ADB shell context.
add_executable(libadbcore.so
        starter.cpp misc.cpp selinux.cpp cgroup.cpp android.cpp)

target_link_libraries(libadbcore.so log cxx::cxx)

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_custom_command(TARGET libadbcore.so POST_BUILD
            COMMAND ${CMAKE_STRIP} --remove-section=.comment "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libadbcore.so")
endif ()

# ----- libadb.so -----
# JNI library implementing the SPAKE2 ADB pairing protocol used by AdbPairingClient.kt.
add_library(adb SHARED adb_pairing.cpp)
target_link_libraries(adb log boringssl::crypto_static cxx::cxx)

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_custom_command(TARGET adb POST_BUILD
            COMMAND ${CMAKE_STRIP} --remove-section=.comment "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libadb.so")
endif ()
