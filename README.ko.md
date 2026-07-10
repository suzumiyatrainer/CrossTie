# 🛠️ CrossTie

Minecraft 1.7.10용 RTM(RealTrainMod) 관련 모드 종합 최적화 및 호환성 패치 모드입니다.

> ⚠️ **빌드 및 개발 시 주의 사항**:
> `src/main/java/jp/kaiz/atsassistmod/block/tileentity/TileEntityIFTTT.java`는 CI 환경(GitHub Actions 등)에서의 컴파일 오류를 방지하기 위한 **컴파일 전용 더미 클래스(스텁)**입니다.
> 빌드 시 최종 배포 JAR 파일에서는 자동으로 제외(exclude)되므로, 게임 실행 시 동작에는 영향을 주지 않습니다.

[![日本語](README.md)](/README.md)
[![English](README.en.md)](/README.en.md)
[![한국어](README.ko.md)](/README.ko.md)
[![简体中文](README.zh-cn.md)](/README.zh-cn.md)
[![繁體中文](README.zh-tw.md)](/README.zh-tw.md)

---

### 📝 프로젝트 개요
CrossTie는 RTM / NGTLib / MCTE (KaizPatchX), Angelica, Bamboo, IntelliInput, GTNHLib, Hodgepodge, LiteLoader / Macro / Keybind Mod, WorldEdit, ProjectRed, CustomNPC+ 등 여러 모드에 걸친 **렌더링 부하 감소, 업데이트 빈도 억제 및 호환성 수정**을 단일 JAR로 제공합니다.

> 💡 **자동 감지 기능**: 대상 모드는 시작 시 자동으로 감지되며, 존재하는 경우에만 해당 패치가 활성화됩니다.

---

## 📊 상태
[![Downloads(latest release)](https://img.shields.io/github/downloads/suzumiyatrainer/CrossTie/latest/total?style=flat-square&color=green&label=다운로드%20수%28최신%20릴리스%29)](https://github.com/suzumiyatrainer/CrossTie/releases/latest)

### 🔧 실행 환경 & 빌드 상태
| 항목 | 상태 / 버전 |
| --- | --- |
| **Build** | [![Build](https://github.com/suzumiyatrainer/CrossTie/actions/workflows/build-and-test.yml/badge.svg)](./.github/workflows/build-and-test.yml) |
| **Minecraft** | `1.7.10` |
| **Forge** | `10.13.4.1614` |
| **Java** | `25(mod:8)` |
| **Gradle** | `9.6.1` |
| **필수 모드** | `UniMixins 0.3.1+` |
| **빌드 시스템** | RetroFuturaGradle 2.0.2 |
| **최종 확인** | `2026-07-10` |

### 🔍 내부 구조 인덱스
* **Mixin 제어**: [`CrossTieMixinPlugin.java`](./src/main/java/net/suzumiya/crosstie/mixins/CrossTieMixinPlugin.java)
* **Late Mixin 제어**: [`CrossTieLateMixinPlugin.java`](./src/main/java/net/suzumiya/crosstie/mixins/CrossTieLateMixinPlugin.java)
* **ASM CoreMod**: [`CrossTieCorePlugin.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieCorePlugin.java)
* **ASM Transformer**: [`CrossTieClassTransformer.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieClassTransformer.java)
* **감지 가능 모드**: `RealTrainMod`, `NGTLib`, `MCTE`, `Angelica`, `Bamboo`, `IntelliInput`, `GTNHLib`, `Hodgepodge`, `LiteLoader`, `MacroMod`, `Keybind Mod`, `RailMapCustom`, `MinFo`, `WorldEdit`, `ProjectRed`, `CustomNPC+`, `ATSAssist`, `SignPicture`, `ArchitectureCraft`

---

## 🚀 권장 구성

원활한 플레이를 위한 권장 모드 버전 구성입니다.

| 모드명 | 권장 버전 | 구분 |
| --- | --- | --- |
| **CrossTie** | `1.0.0-Alpha7` | **코어** |
| **UniMixins** | `0.3.1` | **필수** |
| **KaizPatchX** | `1.10.0` | 권장 |
| **Angelica** | `2.1.51` | 권장 |
| **GTNHLib** | `0.11.23+` | 권장 |
| **Hodgepodge** | `2.7.171+` | 선택 |
| **ArchaicFix** | `0.8.0+` | 선택 |
| **ShaderFixer** | `5.4+` | 선택 |

---

## ⚡ 이 모드의 기능

CrossTie는 RTM 관련 모드군과 성능 최적화 모드군 사이에서 발생하는 다음 **4가지 핵심 항목**을 통합하여 해결하고 제공합니다.

1. **🏃 FPS 최적화**
   * LargeRail 렌더링 최적화, 스크립트 실행 최적화, 디스플레이 리스트 최적화 등.
   * 자세히: [`doc/RTM・NGTLib関連_パフォーマンス最適化.md`](./doc/RTM・NGTLib関連_パフォーマンス最適化.md)

2. **⏳ TPS / 서버 부하 최적화**
   * Train Entity 업데이트 빈도 최적화, 네트워크 부하 감소, 객체 풀 최적화 등.
   * 자세히: [`doc/RTM・NGTLib関連_パフォーマンス最適化.md`](./doc/RTM・NGTLib関連_パフォーマンス最適化.md)

3. **🤝 호환성 및 렌더링 버그 수정**
   * Angelica 및 OptiFine 환경에서의 RTM 렌더링 버그 수정, 기타 주변 모드 충돌 수정.
   * 자세히:
     * [`doc/Angelica・GTNHLib関連互換性修正.md`](./doc/Angelica・GTNHLib関連互換性修正.md)
     * [`doc/OptiFine・FastCraft関連互換性修正.md`](./doc/OptiFine・FastCraft関連互換性修正.md)
     * [`doc/RTM・NGTLib関連_バグ修正.md`](./doc/RTM・NGTLib関連_バグ修正.md)
     * [`doc/その他周辺Mod互換性修正.md`](./doc/その他周辺Mod互換性修正.md)

4. **✨ 새로운 기능 추가**
   * 재시작 없는 모델 팩 재로드 기능, 가선 삭제 기능, 차내 방송용 사운드 API 추가 등.
   * 자세히: [`doc/新規機能の使い方/`](./doc/新規機能の使い方/)

---

## 🏗️ 아키텍처

CrossTie는 적절한 단계에서 안전하게 개입하는 **3계층 패치 메커니즘**을 가지고 있습니다.

### 1. ASM CoreMod (`CrossTieCorePlugin`)
Minecraft 시작 직후 가장 초기 단계에서 `IFMLLoadingPlugin`으로 작동합니다.
* **ModDetector**: `mods/` 폴더를 스캔하여 JAR/ZIP/litemod 파일명에서 설치된 모드를 자동 감지.
* **MinFo 감지 + Angelica 설정 자동 조정**: MinFo가 감지된 경우 `config/angelica-modules.cfg`의 `B:enableFontRenderer`를 강제로 `false`로 재작성합니다.

### 2. ASM Class Transformer (`CrossTieClassTransformer`)
Mixin 단계 이전 클래스 로드 시에 바이트 코드를 직접 재작성합니다. Mixin으로 해결할 수 없는 '로드 순서 문제'나 'MixinTargetAlreadyLoadedException'을 완벽하게 회피합니다.

| 대상 클래스 / 메서드 | 내용 |
| --- | --- |
| **GTNHLib 0.9.x** `MixinBlock_IconWrapper` | `nhlib$getParticleIcon`을 `GtnhLibIconCompat`으로 리디렉션 |
| **Angelica CTM** `MixinRenderBlocks` | `tweakPaneIcons` 등의 glass pane 아이콘 해결을 `AngelicaPaneIconCompat`으로 리디렉션 |
| **MCPatcher** `GlassPaneRenderer` | `setupIcons`를 `return false`로 치환 |
| **Hodgepodge** `StringPooler$GuavaPooler` | `getString(s)` → `s.intern()`으로 치환 (Guava 클래스 로더 충돌 회피) |
| **NGTLib/RTM** `ScriptUtil` | `doScript(String)` → `ScriptUtilFallback.doScript(String)` (Nashorn 부재 환경 대응) |
| **MacroMod** `MacroModPermissions` | 모든 메서드에서 `tamperCheck()` 호출 제거 |
| **LiteLoader** `PermissionsManagerClient` | `tamperCheck()` → no-op 처리 |
| **SplashProgress$3** (`SplashProgress$3`) | `run()` 시작 부분에 리플렉션을 통한 GL 상태 리셋(`GL_TEXTURE_2D` + `glColor4f`) 주입 |

### 3. Mixin (동적 적용)
`CrossTieMixinPlugin`이 감지된 설치 모드에 따라 필요한 Mixin만을 동적으로 적용합니다.
클래스 로더 문제를 방지하기 위해 일부 Mixin은 `CrossTieLateMixinPlugin`을 통한 지연 로드로 적용됩니다（예: ProjectRed）.

상세한 패치 내용은 `doc/` 디렉터리 내의 각 문서를 참조해 주십시오.

---

## 📥 설치 방법

1. 다운로드한 `CrossTie-*.jar`를 `mods` 폴더에 넣습니다.
2. 필수 모드인 `UniMixins 0.3.1+`를 `mods` 폴더에 넣습니다.
3. 목적에 따라 RTM / Angelica / Bamboo / IntelliInput 등의 대상 모드를 추가합니다.
4. 평소처럼 게임을 시작합니다.

---

## 🛠️ 빌드 및 개발

이 프로젝트는 빌드 시스템으로 **RetroFuturaGradle (RFG)**을 사용하고 있습니다.

### 🧱 빌드 방법
표준 빌드 절차입니다. 컴파일된 `.jar` 파일은 `build/libs/` 내에 생성됩니다.
```bash
./gradlew build --no-daemon
```

### 💻 개발 환경 설정

#### IntelliJ IDEA
1. 명령 프롬프트 등에서 다음 명령을 실행하여 IDEA용 프로젝트 파일을 생성합니다.
```bash
./gradlew idea
```
2. IntelliJ IDEA에서 폴더를 열고 프로젝트를 가져옵니다.

#### Eclipse
1. 다음 명령을 실행하여 Eclipse용 프로젝트 파일을 생성합니다.
```bash
./gradlew eclipse
```
2. Eclipse에서 '기존 프로젝트를 작업 공간으로 가져오기'를 통해 프로젝트를 불러옵니다.

### ▶️ 개발 환경에서 실행
IDE 내에서 자동 생성된 실행 설정(Run Configuration)을 사용하거나, 다음 명령으로 테스트용 클라이언트를 시작할 수 있습니다.
```bash
./gradlew runClient
```