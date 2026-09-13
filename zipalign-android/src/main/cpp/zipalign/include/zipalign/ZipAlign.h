/*
 * Copyright (C) 2008 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0.
 */
#ifndef __LIBS_ZIPALIGN_H
#define __LIBS_ZIPALIGN_H

#ifdef __cplusplus
extern "C" {
#endif

namespace android {

/*
 * alignTo is the normal alignment in bytes. sharedLibraryPageAlignment is the
 * alignment in bytes for uncompressed .so entries, or 0 to disable special
 * shared-library alignment. Modern Android zipalign -P values map to
 * 4096/16384/65536 here.
 */
int process(const char* input, const char* output, int alignTo,
        int sharedLibraryPageAlignment, bool force);
int verify(const char* fileName, int alignTo,
        int sharedLibraryPageAlignment, bool verbose);

};

#ifdef __cplusplus
}
#endif
#endif
