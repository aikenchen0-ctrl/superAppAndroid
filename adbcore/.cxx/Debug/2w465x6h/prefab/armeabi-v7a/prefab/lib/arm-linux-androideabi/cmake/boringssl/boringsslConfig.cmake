if(NOT TARGET boringssl::crypto_static)
add_library(boringssl::crypto_static STATIC IMPORTED)
set_target_properties(boringssl::crypto_static PROPERTIES
    IMPORTED_LOCATION "D:/DevelopKits/Libs/caches/9.7.1/transforms/0216b30170e8f334399bb2a3ff54fce3/transformed/boringssl-20250114/prefab/modules/crypto_static/libs/android.armeabi-v7a/libcrypto_static.a"
    INTERFACE_INCLUDE_DIRECTORIES "D:/DevelopKits/Libs/caches/9.7.1/transforms/0216b30170e8f334399bb2a3ff54fce3/transformed/boringssl-20250114/prefab/modules/crypto_static/include"
    INTERFACE_LINK_LIBRARIES ""
)
endif()

if(NOT TARGET boringssl::ssl_static)
add_library(boringssl::ssl_static STATIC IMPORTED)
set_target_properties(boringssl::ssl_static PROPERTIES
    IMPORTED_LOCATION "D:/DevelopKits/Libs/caches/9.7.1/transforms/0216b30170e8f334399bb2a3ff54fce3/transformed/boringssl-20250114/prefab/modules/ssl_static/libs/android.armeabi-v7a/libssl_static.a"
    INTERFACE_INCLUDE_DIRECTORIES "D:/DevelopKits/Libs/caches/9.7.1/transforms/0216b30170e8f334399bb2a3ff54fce3/transformed/boringssl-20250114/prefab/modules/ssl_static/include"
    INTERFACE_LINK_LIBRARIES "boringssl::crypto_static"
)
endif()

