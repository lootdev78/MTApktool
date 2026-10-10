# Additional integration notices

MTApktool's existing license and the licenses of its existing source modules remain in place.

The supplied MP-Manager project was used as a workflow/UI reference. Its GPL Java APK/file-manager implementation is not bundled as a new module or copied wholesale; the new file/tool workflows are implemented in MTApktool's own source. Four requested APK workflows are now independently adapted: signature-verification patching, resource-name refactoring, optimization and REAndroid protection. Decompilation/build stays on apktool-a, and the existing APK cloner remains in use. Selected Apache-licensed REAndroid refactor/protect classes extend the existing antisplit-m module; they retain their headers.

## Material icons and symbols

Standard Google Material glyphs matching the reference menu, selection bar, open-with chooser and editor navigation/save buttons are included as Android vectors. Resource names and tint handling were adapted to MTApktool's theme. Copyright Google; Apache License 2.0. License: `third_party/material-icons/LICENSE`.

- https://github.com/google/material-design-icons
- https://developers.google.com/fonts/docs/material_icons

## Supplied FTP libraries

| Library | Version | License/notice |
| --- | --- | --- |
| Apache Commons Net | 3.11.1 | `mt-ftp/licenses/commons-net-3.11.1/` |
| Apache FTPServer Core | 1.1.1 | `mt-ftp/licenses/ftpserver-core-1.1.1/` |
| Apache FTPLet API | 1.1.1 | `mt-ftp/licenses/ftplet-api-1.1.1/` |
| Apache MINA Core | 2.0.16 | `mt-ftp/licenses/mina-core-2.0.16/` |
| SLF4J API | 1.7.21 | MIT, `mt-ftp/licenses/slf4j-api-1.7.21/LICENSE.txt` |

The Apache library LICENSE/NOTICE files were retained from their supplied JARs. The SLF4J notice is from its v_1.7.21 source: https://raw.githubusercontent.com/qos-ch/slf4j/v_1.7.21/LICENSE.txt . Corresponding copies are bundled in `app/src/main/assets/licenses/` so Android resource packaging exclusions do not remove the notices from the application.

## REAndroid APKEditor additions

License and adaptation notice: `third_party/reandroid-apkeditor/`. Command adapters, option serialization, ZIP optimization, Pairip method matching, task integration and MT dialogs are implemented in the MTAPKTool source. No alternate REAndroid decompiler or cloner and no duplicate APKEditor JAR are added.

## Supplied signature payload

The MT signature-hook option uses the supplied DEX and native payload files; RePairip uses the supplied PairipLog DEX helper. Exact source provenance and SHA-256 values are retained in `third_party/signature-killer/PROVENANCE.md`, with the originating archive's license. The source archive did not supply a separate license for those binaries; this notice does not label them as MIT or Apache-licensed. Copies of these notices are packaged as app assets.
