package com.zhifaios.eyes.aispect;

final class AispectPendingModelActivation {
    static final class Model {
        final String modelId;
        final String version;

        Model(String modelId, String version) {
            this.modelId = modelId == null ? "" : modelId;
            this.version = version == null ? "" : version;
        }

        boolean matches(String candidateModelId, String candidateVersion) {
            String safeModelId = candidateModelId == null ? "" : candidateModelId;
            String safeVersion = candidateVersion == null ? "" : candidateVersion;
            return modelId.equals(safeModelId) && version.equals(safeVersion);
        }
    }

    private Model pending;

    synchronized Model replace(String modelId, String version) {
        Model previous = pending;
        pending = new Model(modelId, version);
        return previous;
    }

    synchronized Model takeIfIdle(boolean touchActive) {
        if (touchActive || pending == null) {
            return null;
        }
        Model output = pending;
        pending = null;
        return output;
    }

    synchronized boolean hasPending() {
        return pending != null;
    }
}
