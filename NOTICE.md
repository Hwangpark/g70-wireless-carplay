# NOTICE

This project is a modified version of the **Wireless CarPlay + Android Auto** mod
(`mods/wireless-carplay-aa`, version 2.1.0) of **HMG-API** by VysseCodes and
contributors, at commit `4fab437` (HMG-API 1.0.0-beta.5). HMG-API now lives at
https://github.com/Open-HMG/HMG-API (formerly https://github.com/VysseCodes/HMG-API),
and the mod has its own repository, https://github.com/Open-HMG/hmgapi-wireless-cp-aa.
HMG-API is licensed under the GNU Affero General Public License v3.0; this project is
distributed under the same license (see `LICENSE`).

What this project changes:

- `hmgapi/make-g70-mod.py` copies the upstream mod and changes its `mod.json`
  (id, name, version, authors, allowed system, feature flags), `patches.json` (car set)
  and `settings.json` (the G70 settings), and writes the G70 stock md5 list.
- `hmgapi/src-patches/*.patch`, applied in order, modify the upstream mod's Java sources
  (`Wireless.java`, `dipo/Bridge.java`, `dipo/CpTurns.java`, `dipo/Dipo.java`) and add
  this project's own classes (`G70Research.java`, `dipo/Research.java`,
  `dipo/G70Extra.java`, `dipo/G70Car.java`, `dipo/G70Drive.java`, `dipo/G70Accel.java`,
  `dipo/G70Mute.java`, `dipo/G70Dark.java`, `dipo/G70Lanes.java`). The patches are this
  project's work.
- Everything else in the built package (the recipes, `libleafiaaac.so`, the rest of the
  Java code) is the upstream mod's, unchanged.
- The USB kits (`usb-*`) are this project's own NEMESIS scripts.

The source for the distributed package `release/wireless-carplay-g70-2.6.7.zip` is this
repository together with the upstream commit named above.

Other projects this work relies on or learned from:

- NEMESIS by xwtk, https://github.com/xwtk/hmg_nemesis (AGPL-3.0): the tool that
  installs packages on these head units. HMG-API builds on it.
- HMG Leafia Plus (archived), https://github.com/VysseCodes/HMG-Leafia-Plus: HMG-API's
  predecessor.
- REA, https://github.com/morluto/rea: the recording method (observed / derived /
  inferred).

No Hyundai, Kia, Genesis or Apple software is included in this repository, and no
firmware keys. Hook map files hold only class and method names found on the unit, and
`g70-stock.md5` only file checksums, as HMG-API does for the cars it supports. The
recipes are the upstream mod's and hold no stock bytes.

Genesis, Hyundai, Apple, CarPlay, iPhone and TMAP are trademarks of their owners. This
project is not affiliated with or endorsed by any of them.
