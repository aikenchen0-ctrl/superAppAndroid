if(NOT TARGET cxx::cxx)
add_library(cxx::cxx STATIC IMPORTED)
set_target_properties(cxx::cxx PROPERTIES
    IMPORTED_LOCATION "D:/DevelopKits/Libs/caches/8.10.2/transforms/b0ebb6f8d6d3bb0005bb4c0188fa1a7d/transformed/libcxx-27.0.12077973/prefab/modules/cxx/libs/android.x86_64/libcxx.a"
    INTERFACE_INCLUDE_DIRECTORIES "D:/DevelopKits/Libs/caches/8.10.2/transforms/b0ebb6f8d6d3bb0005bb4c0188fa1a7d/transformed/libcxx-27.0.12077973/prefab/modules/cxx/include"
    INTERFACE_LINK_LIBRARIES ""
)
endif()

