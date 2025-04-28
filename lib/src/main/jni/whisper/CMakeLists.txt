cmake_minimum_required(VERSION 3.10)

project(whisper-android)

set(CMAKE_CXX_STANDARD 17)

# Find required libraries
find_library(LOG_LIB log)
find_library(ANDROID_LIB android)

# Define source files
set(SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/jni.c
)

# Add definition to prevent sysinfo.h issues
add_definitions(-DNO_SYSINFO)

# Create the main library
add_library(whisper_android_lib SHARED ${SOURCES})

# Include directories
target_include_directories(whisper_android_lib PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# Link libraries
target_link_libraries(whisper_android_lib
    ${LOG_LIB}
    ${ANDROID_LIB}
)

# Create ARM64 variant with FP16 support
if(ANDROID_ABI STREQUAL "arm64-v8a")
    add_library(whisper_v8fp16_va SHARED ${SOURCES})
    
    target_include_directories(whisper_v8fp16_va PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
    )
    
    target_compile_definitions(whisper_v8fp16_va PRIVATE GGML_USE_FP16_ARM)
    
    target_link_libraries(whisper_v8fp16_va
        ${LOG_LIB}
        ${ANDROID_LIB}
    )
endif()

# Create ARMV7 variant with VFPV4 support
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    add_library(whisper_vfpv4 SHARED ${SOURCES})
    
    target_include_directories(whisper_vfpv4 PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
    )
    
    target_compile_definitions(whisper_vfpv4 PRIVATE GGML_USE_VFPV4)
    
    target_link_libraries(whisper_vfpv4
        ${LOG_LIB}
        ${ANDROID_LIB}
    )
endif()

