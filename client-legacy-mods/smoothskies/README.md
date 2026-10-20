<a href="https://ko-fi.com/microcontrollers" target="_blank">
  <img alt="kofi-singular-alt" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/donate/kofi-singular-alt_vector.svg">
</a>

<a href="https://modrinth.com/mod/smooth-skies" target="_blank" rel="nofollow">
  <img alt="modrinth" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_vector.svg">
</a>

<a href="https://www.curseforge.com/minecraft/mc-mods/smooth-skies" target="_blank">
  <img alt="curseforge" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/curseforge_vector.svg">
</a>

# Smooth Skies

A very simple mod that smooths out the fog color when using high render distances, such as with Nvidium! Also has other customizable features and fixes, see gallery for screenshots and section below!

## Features

- Set Fog View Distance
  - Fixes the hard fog cutoff when using high render distances
- Lower Sky Void Darkness
  - Sets the void darkness to only show close to the world's lowest Y value instead of a fixed value
  - This fixes the black void you can sometimes see while below sea level (obvious in skyblock-type worlds)
- Clear Skies
  - Removes the fog color altogether, making the sky a single color
  - Ported from grondag's Clear Skies mod
- Remove Sky in the Void
  - Stops rendering the sky when the player is below the minimum build height (the void)
  - This fixes [MC-249655](https://bugs.mojang.com/browse/MC/issues/MC-249655)
- Fix skybox getting cut off (i.e. holes/gapes) at low render distances
  - This fix is always applied and cannot be disabled
  - Notably fixes Limbo on Hypixel
  - This fixes [MC-298225](https://bugs.mojang.com/browse/MC/issues/MC-298225)
