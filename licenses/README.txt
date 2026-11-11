Raphael itself is under the MIT licence (see LICENSE). Its logo is the author's own art, used
with permission and not under MIT.

The Windows installer also carries a C toolchain, in the mingw64 folder, which Raphael runs as
separate programs (it doesn't link to them):

  WinLibs 16.2.0 r3 (UCRT, POSIX threads), by Brecht Sanders: https://winlibs.com/
    - GCC 16.2.0 (the compiler), under the GNU GPL version 3 with the GCC Runtime Library Exception
    - GNU Binutils (the assembler and linker), under the GNU GPL version 3
    - GDB 18.1 (the debugger), under the GNU GPL version 3, with Python 3.9 (PSF licence)
    - mingw-w64 14.0.0 (Windows headers and libraries), under the ZPL and other permissive licences

  The complete sources of this exact build are at
  https://github.com/brechtsanders/winlibs_mingw/releases/tag/16.2.0posix-23.1.3-14.0.0-ucrt-r3
  (and GCC's, Binutils' and GDB's at https://www.gnu.org/software/).

The GNU General Public License: https://www.gnu.org/licenses/gpl-3.0.html
Programs that Raphael compiles with GCC are the user's own; the runtime library exception means
GCC's licence doesn't apply to them.
