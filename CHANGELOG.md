# Changelog

## [0.14.0](https://github.com/pcvolkmer/mv64e-etl-processor/compare/v0.13.1...v0.14.0) (2026-01-22)


### ⚠ BREAKING CHANGES

* allow missing GenomDE gICS domain name ([#244](https://github.com/pcvolkmer/mv64e-etl-processor/issues/244))
* do not delete patient information if no consent is given ([#236](https://github.com/pcvolkmer/mv64e-etl-processor/issues/236))

### Features

* allow missing GenomDE gICS domain name ([#244](https://github.com/pcvolkmer/mv64e-etl-processor/issues/244)) ([2ba333c](https://github.com/pcvolkmer/mv64e-etl-processor/commit/2ba333c771c100ac463f9ca854185ed80c1ac7c4))
* always send dataset without consent ([#243](https://github.com/pcvolkmer/mv64e-etl-processor/issues/243)) ([623eb1b](https://github.com/pcvolkmer/mv64e-etl-processor/commit/623eb1b250e03772f0311ea088de2a9a5885df2e))
* block further initial submissions ([#232](https://github.com/pcvolkmer/mv64e-etl-processor/issues/232)) ([7be9144](https://github.com/pcvolkmer/mv64e-etl-processor/commit/7be91444a867774362eb5b57bdd246fb50189e7d))
* do not delete patient information if no consent is given ([#236](https://github.com/pcvolkmer/mv64e-etl-processor/issues/236)) ([c23e9d7](https://github.com/pcvolkmer/mv64e-etl-processor/commit/c23e9d790e500d17f2f19252dcd7f3a13cd098e3))


### Bug Fixes

* possible sorting errors in bar chart ([#241](https://github.com/pcvolkmer/mv64e-etl-processor/issues/241)) ([8ed5b94](https://github.com/pcvolkmer/mv64e-etl-processor/commit/8ed5b944ad4ff0429da320b38642e8d552706444))
* request reload notification and update button ([d4ef16c](https://github.com/pcvolkmer/mv64e-etl-processor/commit/d4ef16c115b8429637f933038254646a61dd81b1))

## [0.13.1](https://github.com/pcvolkmer/mv64e-etl-processor/compare/v0.13.0...v0.13.1) (2025-12-18)


### Bug Fixes

* handle null values in supporting variants ([#230](https://github.com/pcvolkmer/mv64e-etl-processor/issues/230)) ([deee482](https://github.com/pcvolkmer/mv64e-etl-processor/commit/deee48279c8635d6ec3749130ae70ec3feff449c))

## [0.13.0](https://github.com/pcvolkmer/mv64e-etl-processor/compare/v0.12.6...v0.13.0) (2025-12-16)


### ⚠ BREAKING CHANGES

* update dto lib to version 0.2.0 ([#226](https://github.com/pcvolkmer/mv64e-etl-processor/issues/226))

### deps

* update dto lib to version 0.2.0 ([#226](https://github.com/pcvolkmer/mv64e-etl-processor/issues/226)) ([6cfb847](https://github.com/pcvolkmer/mv64e-etl-processor/commit/6cfb84770832a3e6cfb209c783a9fda52c5c9141))


### Bug Fixes

* do not save PID in test mode ([#228](https://github.com/pcvolkmer/mv64e-etl-processor/issues/228)) ([8e824ea](https://github.com/pcvolkmer/mv64e-etl-processor/commit/8e824ea9f6c6708d7d8af330455347b42aeae057))

## [0.12.6](https://github.com/pcvolkmer/mv64e-etl-processor/compare/v0.12.5...v0.12.6) (2025-12-05)


### Bug Fixes

* do not serialize null values ([#216](https://github.com/pcvolkmer/mv64e-etl-processor/issues/216)) ([05fe7c2](https://github.com/pcvolkmer/mv64e-etl-processor/commit/05fe7c2091cf7ca45c2cfcec8ec506c43417f3e6))

## [0.12.5](https://github.com/pcvolkmer/mv64e-etl-processor/compare/v0.12.4...v0.12.5) (2025-12-04)


### Features

* check MII broad consent ([#213](https://github.com/pcvolkmer/mv64e-etl-processor/issues/213)) ([0c14eef](https://github.com/pcvolkmer/mv64e-etl-processor/commit/0c14eefe6eb2e3b7567ce06b3118b54e1618058b))

## [0.12.4](https://github.com/pcvolkmer/mv64e-etl-processor/compare/v0.12.3...v0.12.4) (2025-12-03)


### Features

* simple HTTP GET based consent fetch ([#208](https://github.com/pcvolkmer/mv64e-etl-processor/issues/208)) ([b56b8c1](https://github.com/pcvolkmer/mv64e-etl-processor/commit/b56b8c1b6cc9a3e8bcd19adde2b832af15d3a526))

## [0.12.3](https://github.com/pcvolkmer/mv64e-etl-processor/compare/v0.12.2...v0.12.3) (2025-12-01)


### Features

* add footer containing version number ([#204](https://github.com/pcvolkmer/mv64e-etl-processor/issues/204)) ([eb6b26d](https://github.com/pcvolkmer/mv64e-etl-processor/commit/eb6b26d33ec16d7880992268af5414b77abe521d))


### Bug Fixes

* show error page ([#207](https://github.com/pcvolkmer/mv64e-etl-processor/issues/207)) ([9a8da24](https://github.com/pcvolkmer/mv64e-etl-processor/commit/9a8da248c04f47ff30db6da22b035894294499d3))

## [0.12.2](https://github.com/pcvolkmer/mv64e-etl-processor/compare/v0.12.1...v0.12.2) (2025-11-28)


### Features

* add alternative endpoints for request ([#196](https://github.com/pcvolkmer/mv64e-etl-processor/issues/196)) ([2f8ccf3](https://github.com/pcvolkmer/mv64e-etl-processor/commit/2f8ccf33d108537ea7cfe398085a25a7bc926406))


### Bug Fixes

* fix possible NPE for MSI patient refs ([70ff8e9](https://github.com/pcvolkmer/mv64e-etl-processor/commit/70ff8e925aa79623bac043c77d5da71f209058d9))
* fix possible NPE for MSI patient refs ([#194](https://github.com/pcvolkmer/mv64e-etl-processor/issues/194)) ([ab2c0a3](https://github.com/pcvolkmer/mv64e-etl-processor/commit/ab2c0a3cec3cd8ed04de90ac74cb4bfdbaa010e5))
* possible NPE for MSI anonymization ([e2b1763](https://github.com/pcvolkmer/mv64e-etl-processor/commit/e2b1763da105dd913bdf12945cdd7d05a7ca9f47))
* possible NPE for MSI anonymization ([#195](https://github.com/pcvolkmer/mv64e-etl-processor/issues/195)) ([86fc3e3](https://github.com/pcvolkmer/mv64e-etl-processor/commit/86fc3e361c9c5e290319a0c049ef0c6c0a0764ad))
