# SignatureKiller and Pairip helper provenance

The payload files under `app/src/main/assets/signature_killer/` and `app/src/main/assets/pairip/log.dex` are retained byte-for-byte from the supplied MP-Manager-main archive. The MTAPKTool injection/configuration workflows are independently implemented in `ApkEditorEngine` and `PairipPatcher`; no MP-Manager Java utility is copied. The Pairip helper supplies the `com.pairip.PairipLog` class used by the startup patch.

The original payload identifies `bin.mt.signature.KillerApplication` and the upstream project https://github.com/L-JINBIN/ApkSignatureKillerEx . The supplied MP-Manager archive includes the GPL-3.0 license retained alongside this notice as `MP-Manager-origin-LICENSE`. No separate payload-specific license file was present in the supplied assets. This notice records provenance rather than assigning a new license to these binaries.

| File | SHA-256 |
| --- | --- |
| `signature_killer/killer.dex` | `d011301d8f0ec5580d98c43f8d8dc87dee5fcbb79ed561de094704569174c04b` |
| `signature_killer/lib/arm64-v8a/libSignatureKiller.so` | `55cf161b7c7552b74fe6c8a1829f647d25644d195bae9a4686368076eb1b793a` |
| `signature_killer/lib/armeabi-v7a/libSignatureKiller.so` | `c9a4c63067593d670b704c84cc4eededa4f8a92c77ad0998c3d52a70095b8ea0` |
| `signature_killer/lib/x86/libSignatureKiller.so` | `aab371068e7f30937bb5f75aaae95671b971d5eb1a6acd1ffd3d8a5912f1a5e3` |
| `signature_killer/lib/x86_64/libSignatureKiller.so` | `1b726eb11330aec783a27f7ad8ea076f89a2a777d6b03bdf4433a3e66f2b9794` |
| `pairip/log.dex` | `c7d0737f02b956edc377cb42779681ff6277483e07621e52a4980a86add29ff6` |
