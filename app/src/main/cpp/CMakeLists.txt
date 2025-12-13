cmake_minimum_required(VERSION 3.22.1)

project("webwrapper")

add_library(
    webwrapper
    SHARED
    native-lib.cpp
    image_processor.cpp
    url_validator.cpp
)

find_library(
    log-lib
    log
)

find_library(
    jnigraphics-lib
    jnigraphics
)

target_link_libraries(
    webwrapper
    ${log-lib}
    ${jnigraphics-lib}
)

