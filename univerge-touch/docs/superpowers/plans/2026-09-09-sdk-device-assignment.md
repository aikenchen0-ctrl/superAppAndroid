# SDK Device Assignment Implementation Plan

> **For agentic workers:** This plan is executed inline in the current workspace with test-first checkpoints.

**Goal:** Make SDK device registration observable and automatically refresh once on start, and add transactional multi-device model assignment in the management backend.

**Architecture:** Keep the existing UUID/device-scoped assignment contract. Add only device display metadata and a background one-shot refresh on the SDK; add a backend batch endpoint that reuses the existing assignment service with one transaction, then switch the Vue form from single-select to multi-select.

**Tech Stack:** Java 8-compatible Android library, JUnit 4, FastAPI/Pydantic, SQLAlchemy, Vue 3/TypeScript/TDesign.

---

### Task 1: SDK registration metadata and refresh state

**Files:**
- Modify `src/main/java/com/zhifaios/eyes/touch/AispectTouchConfig.java`
- Modify `src/main/java/com/zhifaios/eyes/touch/AispectTouchClassifier.java`
- Modify `src/main/java/com/zhifaios/eyes/aispect/AispectDeviceRegistrar.java`
- Modify `src/test/java/com/zhifaios/eyes/aispect/AispectDeviceRegistrarTest.java`
- Add `src/test/java/com/zhifaios/eyes/touch/AispectTouchClassifierApiTest.java`

- [x] Write tests for `deviceName` JSON and public sync result/device metadata access.
- [x] Run targeted SDK tests and observe the expected missing API/JSON assertion failures.
- [x] Add the optional config, compatible registrar overload, derived Android device name, one-shot background refresh, and last-result getter.
- [x] Run targeted tests and then all SDK tests.

### Task 2: Transactional backend batch assignment

**Files:**
- Modify `C:/codeDev/cnn/cnnHouduan/cnnWebManager/app/services/assignments.py`
- Modify `C:/codeDev/cnn/cnnHouduan/cnnWebManager/app/api/admin_assignments.py`
- Modify `C:/codeDev/cnn/cnnHouduan/cnnWebManager/app/api/schemas.py`
- Modify `C:/codeDev/cnn/cnnHouduan/cnnWebManager/tests/test_assignments.py`

- [x] Write tests for two-device success and rollback on an invalid target.
- [x] Run the two tests and observe the endpoint-not-found or partial-commit failures.
- [x] Add `commit` control to the existing service, a batch request/response, and one transaction around all device upserts.
- [x] Run assignment and API-contract tests.

### Task 3: Multi-select management UI

**Files:**
- Modify `C:/codeDev/cnn/cnnHouduan/cnnWebUi/src/api/client.ts`
- Modify `C:/codeDev/cnn/cnnHouduan/cnnWebUi/src/views/AssignmentsView.vue`

- [x] Add the typed batch client call and switch device selection state to an array.
- [x] Submit selected device pairs to the batch endpoint and clear stale selections when scope/platform changes.
- [x] Run the UI typecheck/build.

### Task 4: Documentation and release verification

**Files:**
- Modify `README.md`
- Modify `C:/Users/血饮/Desktop/待完成任务/original/touch_eyes_dataAnalysis/FunctionAndDataStructureDescription.md`

- [x] Document device ID semantics, device name, one-shot refresh, batch assignment, and explicit HTTP policy.
- [x] Run SDK `testDebugUnitTest` and `assembleRelease` using the Windows Java Gradle Wrapper command.
- [x] Run backend tests and UI build.
- [x] Record AAR path and SHA-256.

## Verification Record

- SDK: `testDebugUnitTest` and `assembleRelease` passed with the Windows Java Gradle Wrapper command.
- Backend: `tests/test_assignments.py`, `tests/test_devices_api.py`, and `tests/test_api_route_contract.py`: `9 passed`.
- UI: `npm run build` passed; only existing dependency annotation and chunk-size warnings remain.
