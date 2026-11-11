#!/usr/bin/env bash
# Gets the C toolchain the Windows installer carries: WinLibs (GCC, binutils, mingw-w64 and a gdb
# with Python inside, which Raphael's C tracer needs), pinned and checked, then trimmed to what
# compiling and debugging C and C++ need (it also ships CMake, Fortran, Doxygen and more).
# Usage: scripts/winlibs.sh <folder>   (the toolchain lands in <folder>/mingw64). Needs 7z.
set -euo pipefail
OUT=${1:?folder}
RELEASE=16.2.0posix-23.1.3-14.0.0-ucrt-r3
FILE=winlibs-x86_64-posix-seh-gcc-16.2.0-mingw-w64ucrt-14.0.0-r3.7z
SHA256=db014a73b71907c6c778fd1c938056cddcae9023f89f8dfff7783d9e45baf975

mkdir -p "$OUT"
if [ ! -f "$OUT/$FILE" ]; then
    curl -sSL -o "$OUT/$FILE" "https://github.com/brechtsanders/winlibs_mingw/releases/download/$RELEASE/$FILE"
fi
echo "$SHA256  $OUT/$FILE" | sha256sum -c
rm -rf "$OUT/mingw64"
7z x -y -o"$OUT" "$OUT/$FILE" > /dev/null

cd "$OUT/mingw64"
# Tools Raphael never runs. Every DLL stays: which ones gdb and gcc load isn't worth guessing.
rm -f bin/cmake.exe bin/cpack.exe bin/ctest.exe bin/cmcldeps.exe bin/doxygen.exe bin/cppcheck.exe bin/ccache.exe \
      bin/ctags.exe bin/readtags.exe bin/optscript.exe bin/ninja.exe bin/premake5.exe bin/mingw32-make.exe \
      bin/nasm.exe bin/ndisasm.exe bin/yasm.exe bin/vsyasm.exe bin/ytasm.exe bin/jwasm.exe bin/lto-dump.exe \
      bin/*gfortran*.exe bin/wl-*.exe bin/pedeps* bin/listpedeps.exe bin/copypedeps.exe bin/pexports.exe \
      bin/gendef.exe bin/genidl.exe bin/widl.exe bin/dos2unix.exe bin/unix2dos.exe bin/mac2unix.exe bin/unix2mac.exe \
      bin/pkgconf.exe bin/sarif-replay.exe bin/gprof.exe bin/gcov*.exe bin/bomtool.exe bin/spdxtool.exe \
      bin/pccritic.exe bin/gdbserver.exe bin/ntldd.exe
rm -rf share/cmake-* share/Cppcheck share/info share/man share/locale share/doc
G=libexec/gcc/x86_64-w64-mingw32/16.2.0
rm -rf $G/f951.exe $G/cc1obj.exe $G/cc1objplus.exe $G/lto1.exe $G/plugin
rm -f lib/libgfortran* lib/libpython3.9.a lib/libpython3.9.dll.a lib/libbfd.a lib/libopcodes.a lib/libctf*.a \
      lib/libiberty.a lib/libsframe.a lib/libyasm.a
rm -rf lib/pkgconfig lib/python3.9/test lib/python3.9/idlelib lib/python3.9/tkinter lib/python3.9/turtledemo \
       lib/python3.9/ensurepip lib/python3.9/lib2to3 lib/gcc/x86_64-w64-mingw32/16.2.0/finclude
find lib/python3.9 -name __pycache__ -type d -prune -exec rm -rf {} +
du -sh .
