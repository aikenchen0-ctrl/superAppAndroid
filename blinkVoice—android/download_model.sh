#!/bin/bash
# Downloads the MediaPipe FaceLandmarker model required by the app.
# Run this script once before building the project.

MODEL_URL="https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"
DEST="app/src/main/assets/face_landmarker.task"

echo "Downloading face_landmarker.task..."
curl -L "$MODEL_URL" -o "$DEST"
echo "Saved to $DEST"
