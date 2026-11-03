"""Check all packaged 64-bit ELF LOAD segments for 16 KB page alignment."""
import struct
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as apk:
    checked = []
    for name in apk.namelist():
        if not name.startswith(("lib/arm64-v8a/", "lib/x86_64/")) or not name.endswith(".so"):
            continue
        elf = apk.read(name)
        assert elf[:6] == b"\x7fELF\x02\x01", f"Not little-endian ELF64: {name}"
        offset = struct.unpack_from("<Q", elf, 32)[0]
        size, count = struct.unpack_from("<HH", elf, 54)
        loads = 0
        for index in range(count):
            kind, flags, file_offset, virtual, physical, file_size, mem_size, align = struct.unpack_from(
                "<IIQQQQQQ", elf, offset + index * size
            )
            if kind == 1:
                assert align >= 16384, f"{name}: LOAD alignment {align} < 16384"
                assert (virtual - file_offset) % 16384 == 0, f"{name}: incompatible LOAD offset"
                loads += 1
        assert loads, f"No LOAD segments: {name}"
        checked.append(name)
    assert any("arm64-v8a" in name for name in checked), "Missing ARM64 library"
    print("16 KB ELF alignment verified:", ", ".join(checked))
