package com.sfj3861.gbastudio

/** JNI boundary for native GBA tooling. */
class NativeBridge {
    external fun engineVersion(): String

    companion object {
        init { System.loadLibrary("gbastudio_native") }
    }
}
