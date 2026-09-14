# Integrated third-party source modules

The following user-supplied source projects are integrated into the MTApktool source tree. Their upstream notices/licenses are retained alongside each module.

| MTApktool module | Supplied source | Integration |
| --- | --- | --- |
| `:antisplit-m` | AntiSplit-M redesign | REAndroid-based split APK merge engine used by split-container conversion and APK Extractor |
| `:apkextractor` | APKExtractor | Installed-app extraction engine hosted by MTApktool UI/navigation |
| `:apkcloner` | ApkCloner | APK package/resource cloning engine hosted as an `.apk` function |
| `:mh-editview` | MH TextEditor EditView | Editor view library |
| `:mh-editor` | MH TextEditor app source | Embedded MTApktool text/code editor |

The existing Apktool, apksig, zipalign, smali/antlr and other project components retain their existing notices/licenses.
