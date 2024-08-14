import cv2
import numpy as np
import face_recognition
import pickle
from datetime import datetime
from deepface import DeepFace
import os
import json
from typing import Optional, Dict, Any

def load_known_faces(encoding_file_path: str):
    print(f"numpy version  {np.__version__}   ")
    """Load known faces and their names from the encoding file."""
    if os.path.exists(encoding_file_path):
        with open(encoding_file_path, 'rb') as file:
            known_face_encodings, known_face_names = pickle.load(file)
    else:
        known_face_encodings = []
        known_face_names = []
    return known_face_encodings, known_face_names

def detect_emotion(image_path: str):
    """Detect emotion in the given frame using DeepFace."""
    try:
        # Analyze the image for emotion
        emotion_results = DeepFace.analyze(img_path=image_path, actions=['emotion'])
        # Handle DeepFace library output
        if 'dominant_emotion' in emotion_results[0]:
            return emotion_results[0]['dominant_emotion']
        else:
            return None
    except Exception as e:
        import traceback
        print(f"Emotion detection failed: {e}")
        print(traceback.format_exc())  # Print the full traceback for more context
        return None

def to_dict(status: str, message: Optional[str] = None, name: Optional[str] = None, emotion: Optional[str] = None, time: Optional[str] = None) -> Dict[str, Any]:
    """Convert the result to a dictionary."""
    return {
        "status": status,
        "message": message,
        "name": name,
        "emotion": emotion,
        "time": time
    }

def to_json(status: str, message: Optional[str] = None, name: Optional[str] = None, emotion: Optional[str] = None, time: Optional[str] = None) -> str:
    """Convert the result to a JSON string."""
    return json.dumps(to_dict(status, message, name, emotion, time))

def process_image(image_path: str, encoding_file_path: str) -> str:
    """Process the image to recognize faces and detect emotions."""
    print(f"Image path: {image_path}")
    print(f"Encoding file path: {encoding_file_path}")

    if not os.path.exists(image_path):
        return to_json(status="error", message="Image file not found")
    if not os.path.exists(encoding_file_path):
        return to_json(status="error", message="Encoding file not found")

    frame = cv2.imread(image_path)
    if frame is None:
        return to_json(status="error", message="Failed to load image")

    try:
        accuracy_threshold = 0.50
        known_face_encodings, known_face_names = load_known_faces(encoding_file_path)

        face_locations = face_recognition.face_locations(frame)
        face_encodings = face_recognition.face_encodings(frame, face_locations)

        for (top, right, bottom, left), face_encoding in zip(face_locations, face_encodings):
            matches = face_recognition.compare_faces(known_face_encodings, face_encoding)
            distances = face_recognition.face_distance(known_face_encodings, face_encoding)

            if any(matches):
                best_match_index = np.argmin(distances)
                best_match_name = known_face_names[best_match_index]
                best_match_accuracy = distances[best_match_index]

                if best_match_accuracy < accuracy_threshold:
                    emotion = detect_emotion(frame)
                    timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
                    if emotion is None:
                        message = f"Hey {best_match_name}, your emotion is not detected. Please try again."
                        return to_json(status="error", message=message)
                    else:
                        return to_json(status="success", name=best_match_name, emotion=emotion, time=timestamp)
            else:
                return to_json(status="error", message="Face not recognized with sufficient accuracy")
    except Exception as e:
        return to_json(status="error", message=str(e))

# Define paths to the required files (ensure these paths are accessible in Android environment)
def get_file_paths():
    """Retrieve file paths for image and encoding file."""
    # Adjust paths as necessary for the Android environment
    image_path = 'C:/Users/USER/AppData/Local/Google/AndroidStudioPreview2022.2/device-explorer/samsung SM-A155F/data/data/com.example.detectionpython/cache/temp_image_resized.jpg'  # Example path
    encoding_file_path = '/data/user/0/com.example.detectionpython/files/encodings.pkl'  # Example path
    return image_path, encoding_file_path

# Example usage
if __name__ == "__main__":
    image_path, encoding_file_path = get_file_paths()

    # Ensure the image file and encoding file paths are correct
    if not os.path.isfile(image_path):
        print(f"Image file not found: {image_path}")
    elif not os.path.isfile(encoding_file_path):
        print(f"Encoding file not found: {encoding_file_path}")
    else:
        # Call the process_image function
        result = process_image(image_path, encoding_file_path)
        print(result)
