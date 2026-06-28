# 🛠️ CrossTie

Minecraft 1.7.10용 RTM(RealTrainMod) 계열 종합 최적화 및 호환성 패치 Mod입니다.

[![日本語](README.md)](/README.md)
[![English](README.en.md)](/README.en.md)
[![한국어](README.ko.md)](/README.ko.md)
[![简体中文](README.zh-cn.md)](/README.zh-cn.md)
[![繁體中文](README.zh-tw.md)](/README.zh-tw.md)

---

### 📝 프로젝트 개요
CrossTie는 RTM / NGTLib / MCTE (KaizPatchX), Angelica, Bamboo, IntelliInput, GTNHLib, Hodgepodge, LiteLoader / Macro / Keybind Mod 등 여러 Mod에 걸친 **렌더링 부하 감소, 업데이트 빈도 억제, 호환성 수정**을 하나의 JAR 파일로 제공합니다.

> 💡 **자동 감지 기능**: 대상 Mod는 실행 시 자동으로 감지되며, 설치되어 있는 경우에만 해당 패치가 활성화됩니다.

---

## 📊 상태
[![Downloads(latest release)](https://img.shields.io/github/downloads/suzumiyatrainer/CrossTie/latest/total?style=flat-square&color=green&label=다운로드수%28최신%20릴리스%29)](https://github.com/suzumiyatrainer/CrossTie/releases/latest)

### 🔧 동작 환경 & 빌드 상태
| 항목 | 상태 / 버전 |
| --- | --- |
| **Build** | [![Build](https://github.com/suzumiyatrainer/CrossTie/actions/workflows/build-and-test.yml/badge.svg)](./.github/workflows/build-and-test.yml) |
| **Minecraft** | `1.7.10` |
| **Forge** | `10.13.4.1614` |
| **Java** | `25(mod:8)` |
| **Gradle** | `9.6.0` |
| **필수 Mod** | `UniMixins 0.3.1+` |
| **빌드 시스템** | RetroFuturaGradle 2.0.2 |
| **최종 확인** | `2026-06-28` |

### 🔍 내부 구조 인덱스
* **Mixin 제어**: [`CrossTieMixinPlugin.java`](./src/main/java/net/suzumiya/crosstie/mixins/CrossTieMixinPlugin.java)
* **ASM CoreMod**: [`CrossTieCorePlugin.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieCorePlugin.java)
* **ASM Transformer**: [`CrossTieClassTransformer.java`](./src/main/java/net/suzumiya/crosstie/asm/CrossTieClassTransformer.java)
* **감지 가능 Mod**: `RealTrainMod`, `NGTLib`, `MCTE`, `Angelica`, `Bamboo`, `IntelliInput`, `GTNHLib`, `Hodgepodge`, `LiteLoader`, `MacroMod`, `Keybind Mod`, `RailMapCustom`, `MinFo`

---

## 🚀 추천 구성

쾌적한 동작을 위한 추천 Mod 버전 구성입니다.

| Mod 이름 | 추천 버전 | 구분 |
| --- | --- | --- |
| **CrossTie** | `1.0.0-Alpha7` | **본체** |
| **UniMixins** | `0.3.1` | **필수** |
| **KaizPatchX** | `1.10.0` | 추천 |
| **Angelica** | `2.1.42+` | 추천 |
| **GTNHLib** | `0.11.18+` | 추천 |
| **Hodgepodge** | `2.7.162+` | 선택 |
| **ArchaicFix** | `0.8.0+` | 선택 |
| **ShaderFixer** | `5.4+` | 선택 |

---

## ⚡ 어떤 Mod인가요?

CrossTie는 RTM 관련 Mod 군과 성능 향상 Mod 군 사이에서 발생하는 다음 **3가지 핵심 문제**를 통합하여 해결합니다.

1. **🏃 FPS 최적화**
   * [RTM] LargeRail의 거리 컬링 및 프러스텀 컬링
   * [RTM] LargeRail의 청크 단위 렌더링 일괄 처리
   * [RTM] LargeRail TESR의 거리에 따른 렌더링 빈도 스로틀링
   * [RTM] 레일 테셀레이션 루프 최적화
   * [RTM] 배선 및 선로주 연결 판정 결과 캐싱
   * [RTM] 사인보드 선택 GUI의 가상 스크롤 도입을 통한 열기 지연 대폭 개선
   * [RTM] 신호기/건널목 컬링 비활성화를 통한 원경 렌더링 유지
   * [NGTScriptUtil] 스크립트 실행 (Invocable) 캐시 최적화
   * [RailMapCustom] 레일맵 캐시 최적화
   * [MCTE] 월드 블록 차분 세트 최적화
   * [Angelica] RenderGlobal.displayList의 네이티브 최적화
   * [KaizPatch, NGTScriptUtil, Angelica] 스크립트에서의 GL 호출 리다이렉트 및 캐시 최적화
2. **⏳ TPS / 서버 부하 최적화**
   * [RTM] 256m 이상 떨어진 Train Entity의 클라이언트 측 업데이트 빈도 감소
   * [RTM] Train 속도 DataWatcher 동기화 최적화를 통한 네트워크 부하 감소
   * [RTM] Train onUpdate 내 중복 getBlock() 호출 캐싱
   * [GTNHLib] 스레드 세이프 객체 풀링
3. **🤝 호환성 및 렌더링 버그 수정**
   * [Angelica] 셰이더 활성화 시 바닐라 구름이 이중으로 렌더링되는 문제 수정
   * [Angelica] 셰이더 활성화 시 물의 렌더링 거리가 비정상적으로 적용되는 문제 수정
   * [Angelica, RTM] 블록 리빌드 시 레일 TESR의 라이팅이 업데이트되지 않는 문제 수정
   * [Angelica] 스플래시 화면의 텍스처 상태 캐시 문제 수정
   * [OptiFine, RTM] LargeRail의 UV 좌표 손상(초록색 세로선) 문제 수정
   * [OptiFine, RTM] 와이어 렌더링 시 법선 왜곡으로 인해 셰이더 환경에서 투명해지는 문제 수정
   * [OptiFine, RTM] 그림자 패스(shadow pass) 중 와이어가 렌더링되지 않고 사라지는 문제 수정
   * [GTNHLib] 유리 판 및 블록의 아이콘 표시 및 획득 폴백 수정
   * [Hodgepodge] Guava 클래스 로더 충돌 회피
   * [LiteLoader, MacroMod] 권한 관리 및 코어 호환성 수정
   * [MCTE] 미니어처 블록 및 아이템 미니어처의 동적 조명 수정
   * [KaizPatch] ModelLoaderKt 폴백 수정
4. **✨ 새로운 기능 추가**
   * [RTM] 재시작 없이 모델 팩을 다시 로드하는 기능 추가 (설정 또는 mods→CrossTie→RTM→reloadPacks) *약간의 버그가 있을 수 있지만 대부분 정상적으로 작동합니다.*
   * [RTM] 두 지점 사이의 가공선(와이어)을 삭제하는 기능 추가 (설정된 키 + 우클릭) *맨손일 때만 작동합니다.*

---

## 🏗️ 아키텍처

CrossTie는 **3층 패치 메커니즘**을 가지며, 적절한 단계에서 안전하게 개입합니다.

### 1. ASM CoreMod (`CrossTieCorePlugin`)
`IFMLLoadingPlugin`으로서 Minecraft 실행 직후의 가장 초기 단계에서 동작합니다.
* **ModDetector**: `mods/` 폴더를 스캔하여 JAR/ZIP/litemod 파일 이름으로부터 설치된 Mod를 자동 감지합니다.
* **MinFo 감지 + Angelica 설정 자동 조정**: MinFo가 감지된 경우, `config/angelica-modules.cfg`의 `B:enableFontRenderer`를 강제로 `false`로 변경합니다.

### 2. ASM Class Transformer (`CrossTieClassTransformer`)
Mixin 단계 이전에 클래스 로드 시 바이트코드를 직접 수정합니다. Mixin으로는 대응할 수 없는 '로드 순서 문제'나 `MixinTargetAlreadyLoadedException`을 완전히 회피합니다.

| 대상 클래스 / 메서드 | 내용 |
| --- | --- |
| **GTNHLib 0.9.x** `MixinBlock_IconWrapper` | `nhlib$getParticleIcon`을 `GtnhLibIconCompat`으로 리다이렉트 |
| **Angelica CTM** `MixinRenderBlocks` | `tweakPaneIcons` 등의 Glass pane 아이콘 해결을 `AngelicaPaneIconCompat`으로 리다이렉트 |
| **MCPatcher** `GlassPaneRenderer` | `setupIcons`를 `return false`로 대체 |
| **Hodgepodge** `StringPooler$GuavaPooler` | `getString(s)` → `s.intern()`으로 대체 (Guava 클래스 로더 충돌 회피) |
| **NGTLib/RTM** `ScriptUtil` | `doScript(String)` → `ScriptUtilFallback.doScript(String)` (Nashorn 부재 환경 대응) |
| **MacroMod** `MacroModPermissions` | 모든 메서드에서 `tamperCheck()` 호출을 제거 |
| **LiteLoader** `PermissionsManagerClient` | `tamperCheck()`를 no-op(아무 동작 안 함) 처리 |
| **SplashProgress$3** (`SplashProgress$3`) | `run()` 시작 부분에 리플렉션을 통한 GL 상태 리셋 (`GL_TEXTURE_2D` + `glColor4f`)을 주입 |

### 3. Mixin (동적 적용)
`CrossTieMixinPlugin`이 감지된 설치 Mod에 따라 필요한 Mixin만을 동적으로 적용합니다.

<details>
<summary>🔍 Mod별 Mixin 적용 상세 리스트 (클릭하여 펼치기)</summary>

#### 🔹 Angelica
* **`AngelicaRenderGlobalDisplayListCrashMixin`** (Client + Angelica + `crosstie.enableNativeRenderGlobalDisplayLists=true`)
    * `hi03ExpressRailwayRail` 렌더링 시, Angelica의 디스플레이 리스트를 회피하고 OpenGL 이전 경로를 사용
* **`SplashProgressBlackoutFixMixin`** (Client + Angelica + `enableFontRenderer=false`)
    * 스플래시 화면의 텍스처 상태 캐시 문제를 수정

#### 🔹 GTNHLib
* **`ObjectPoolerThreadSafeMixin`** (GTNHLib 상시)
    * 스레드 세이프한 객체 풀링
* **`MixinBlockPaneFix` / `MixinBlockPaneIconFallback` / `MixinBlockIconFallback`** (Client + GTNHLib)
    * Glass pane 및 블록의 아이콘 표시 및 획득 폴백 수정

#### 🔹 KaizPatchX (NGTScriptUtil / MCTE / RailMapCustom / NGTLib / RTM)
* **`ScriptUtilInvocableCacheMixin`** (NGTScriptUtil)
    * Invocable 캐시 최적화
* **`AngelicaScriptTransformCacheMixin`** (Client + Angelica + KaizPatch + NGTScriptUtil)
    * `AngelicaCompat.transformScript`를 `ScriptGlRedirector`로 인터셉트 (GL 호출 리다이렉트 + 캐시)
* **`ModelPackManagerScriptRedirectMixin`** (Client + Angelica + RTM + NGTScriptUtil)
    * `ModelPackManager.getScript`에 `ScriptGlRedirector`를 적용
* **`RailMapCustomCacheMixin`** (RailMapCustom)
    * 레일맵 캐시 최적화
* **`McteWorldSetBlockDiffMixin`** (MCTE)
    * 월드 블록 차분 세트 최적화
* **`RenderMiniatureDynamicLightMixin` / `RenderItemMiniatureDynamicLightMixin`** (Client + MCTE)
    * 미니어처 블록 및 아이템 미니어처의 동적 라이팅 수정
* **`EntityTrainBaseSpeedSyncMixin` / `EntityTrainBaseOptimizationMixin`** (RTM)
    * 열차 속도 동기화 및 엔티티 업데이트 최적화
* **`RenderElectricalWiringConnectionCacheMixin` / `BlockLinePoleConnectionCacheMixin`** (Client + RTM)
    * 배선 렌더링 및 선로주 연결 캐싱
* **`RenderLargeRailOptimizationMixin` / `RenderLargeRailChunkBatchMixin`** (Client + RTM)
    * 대형 레일 렌더링 최적화 및 청크 배치 처리
* **`RailPartsRendererOptimizationMixin`** (Client + RTM)
    * 레일 파츠 렌더러 최적화

#### 🔹 LiteLoader / MacroMod
* **`MixinPermissionsManagerClient` / `MacroModCoreMixin`**
    * 권한 관련 및 코어 호환성 수정

</details>

---

## 📦 대응 Mod 상세

| Mod | 감지 이름 | 주요 패치 및 대응 내용 |
| --- | --- | --- |
| **RealTrainMod** | `RTM` | 렌더링 최적화, 업데이트 간소화, GL 리다이렉트 |
| **NGTLib** | `NGTLib` / `NGTScriptUtil` | ScriptUtil 호환, GL 리다이렉트 |
| **MCTE** | `MCTE` | 미니어처 렌더링 수정, 동적 라이팅 |
| **KaizPatch** | `KaizPatch` | Angelica 연동, 스크립트 캐시 |
| **Angelica** | `Angelica` / `AngelicaGlsm` | 디스플레이 리스트 충돌 수정, 설정 자동 조정 |
| **Bamboo** | `Bamboo` | 렌더링 컬링, 업데이트 빈도 억제 (하위 호환) |
| **IntelliInput** | `IntelliInput` | IME 콜백 안정화 (하위 호환) |
| **GTNHLib** | `GTNHLib` | 아이콘 해결, 스레드 세이프화 |
| **Hodgepodge** | `Hodgepodge` | Guava 클래스 로더 충돌 회피 |
| **LiteLoader** | `LiteLoader` | 권한 관리 수정 |
| **MacroMod** | `MacroMod` | `tamperCheck` 제거, 권한 수정 |
| **Keybind Mod** | *(동봉 감지)* | `tamperCheck` 제거 |
| **RailMapCustom** | `RailMapCustom` | 레일맵 캐시 |

---

## 📥 설치 방법

1. 다운로드한 `CrossTie-*.jar` 파일을 `mods` 폴더에 넣습니다.
2. 필수 파일인 `UniMixins 0.3.1+`를 `mods` 폴더에 넣습니다.
3. 목적에 따라 RTM / Angelica / Bamboo / IntelliInput 등의 대상 Mod를 추가합니다.
4. 평소대로 게임을 실행합니다.

---

## 🛠️ 빌드 및 개발

### 🧱 빌드 절차
```bash
./gradlew build --no-daemon
```