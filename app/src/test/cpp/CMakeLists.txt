cmake_minimum_required(VERSION 3.22)
project(samsonic_native_tests CXX)

# Host-side tests for the driver's plain C++ (no Android, no USB). Run by CI and on a PC:
#   cmake -S app/src/test/cpp -B build/native-tests -DSAMSONIC_SANITIZE=address,undefined
#   cmake --build build/native-tests && ctest --test-dir build/native-tests --output-on-failure
# Use -DSAMSONIC_SANITIZE=thread to check the ring's single-producer, single-consumer hand-off.
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(SAMSONIC_SANITIZE "" CACHE STRING "Value for -fsanitize=, e.g. address,undefined or thread")
if(SAMSONIC_SANITIZE)
    add_compile_options(-fsanitize=${SAMSONIC_SANITIZE} -fno-omit-frame-pointer -g)
    add_link_options(-fsanitize=${SAMSONIC_SANITIZE})
endif()
add_compile_options(-Wall -Wextra)

find_package(Threads REQUIRED)
add_executable(byte_ring_test byte_ring_test.cpp)
target_include_directories(byte_ring_test PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/../../main/cpp)
target_link_libraries(byte_ring_test Threads::Threads)

enable_testing()
add_test(NAME byte_ring COMMAND byte_ring_test)
