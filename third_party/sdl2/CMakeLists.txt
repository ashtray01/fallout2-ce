# Build static lib only
set(SDL_SHARED OFF)
set(SDL_STATIC ON)

# Fixes _ftol2_sse already defined
set(SDL_LIBC ON)

if (ANDROID)
    set(SDL_STATIC_PIC ON)
endif()

include(FetchContent)

if(ANDROID)
    set(FALLOUT_SDL_TAG "release-2.32.10")
else()
    set(FALLOUT_SDL_TAG "release-2.26.1")
endif()

FetchContent_Declare(sdl2
    GIT_REPOSITORY "https://github.com/libsdl-org/SDL"
    GIT_TAG "${FALLOUT_SDL_TAG}"
    GIT_SHALLOW TRUE
    GIT_PROGRESS TRUE
    EXCLUDE_FROM_ALL
)

FetchContent_MakeAvailable(sdl2)

set(SDL2_INCLUDE_DIRS ${sdl2_SOURCE_DIR} ${sdl2_BINARY_DIR} PARENT_SCOPE)
set(SDL2_LIBRARIES SDL2-static SDL2::SDL2main PARENT_SCOPE)
