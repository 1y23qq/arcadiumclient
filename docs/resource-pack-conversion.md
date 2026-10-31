# Resource pack conversion

Arcadium adapts resource packs for Minecraft 1.7.10, 1.8.9, 1.21.11 and 26.2.
The pack list keeps the original file names, folders, descriptions and icons.
Conversion runs when a selected pack is applied, and cached copies are kept
under the game data directory's `arcadium/cache/resource-packs` folder.
Original folders and ZIP files are never edited.

The converter handles legacy block/item names and folders, texture references
in item models, armor and glint paths, split interface/particle/effect atlases,
and the entity texture moves and bed/sign layouts introduced in 26.2.
Reverse conversion retains alternative legacy names where several old files
correspond to one modern texture. Missing sprites and unknown items use the
target game's resources. Incompatible shader programs are omitted; this is not
an implementation of another version's shader or mod APIs.

Cached results are invalidated when the source pack or conversion schema changes.
Reverse atlas assembly starts with the target game's own atlas so parts absent
from the selected pack retain their normal appearance. Conversion tables and
their original MIT notices are stored in `client-shared/src/main/resources`.
