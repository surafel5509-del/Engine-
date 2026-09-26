# S Engine native layer (NDK): C++ script VM + landscape generator, exposed to Kotlin over JNI.
cmake_minimum_required(VERSION 3.22.1)
project(sengine LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

file(GLOB SENGINE_SOURCES CONFIGURE_DEPENDS
        ${CMAKE_CURRENT_SOURCE_DIR}/script/*.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/engine/*.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/jni/*.cpp)

add_library(sengine SHARED ${SENGINE_SOURCES})
target_include_directories(sengine PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_options(sengine PRIVATE -fexceptions -frtti -Wall -Wno-unused-parameter
        $<$<CONFIG:Release>:-O3> $<$<CONFIG:RelWithDebInfo>:-O3> $<$<CONFIG:Debug>:-O2>)
find_library(log-lib log)
target_link_libraries(sengine ${log-lib})
# 16 KB page-size compatible (Android 15+ devices)
target_link_options(sengine PRIVATE "-Wl,-z,max-page-size=16384")
