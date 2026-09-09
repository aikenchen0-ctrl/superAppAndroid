if(NOT TARGET cxx::cxx)
add_library(cxx::cxx STATIC IMPORTED)
set_target_properties(cxx::cxx PROPERTIES
    IMPORTED_LOCATION "D:/DevelopKits/Libs/caches/9.7.1/transforms/783b772a0445a4b2e0d4af806eda1ac8/transformed/libcxx-27.0.12077973/prefab/modules/cxx/libs/android.x86_64/libcxx.a"
    INTERFACE_INCLUDE_DIRECTORIES "D:/DevelopKits/Libs/caches/9.7.1/transforms/783b772a0445a4b2e0d4af806eda1ac8/transformed/libcxx-27.0.12077973/prefab/modules/cxx/include"
    INTERFACE_LINK_LIBRARIES ""
)
endif()

