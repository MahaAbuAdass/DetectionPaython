import cv2
import numpy as np
import face_recognition
import pickle
from datetime import datetime
from deepface import DeepFace
import os
import csv
import json
from typing import Optional, Dict, Any

class FaceRecognitionProcessor:
    def __init__(self, encoding_file_path: str, csv_file_path: str):
        self.encoding_file_path = encoding_file_path
        self.csv_file_path = csv_file_path

    def load_known_faces(self):
        """Load known faces and their names from the encoding file."""
        if os.path.exists(self.encoding_file_path):
            with open(self.encoding_file_path, 'rb') as file:
                known_face_encodings, known_face_names = pickle.load(file)
        else:
            known_face_encodings = []
            known_face_names = []
        return known_face_encodings, known_face_names

    def detect_emotion(self, frame):
        """Detect emotion in the given frame using DeepFace."""
        try:
            temp_path = 'temp_frame.jpg'
            cv2.imwrite(temp_path, frame)
            emotion_results = DeepFace.analyze(img_path=temp_path, actions=['emotion'])
            os.remove(temp_path)
            return emotion_results[0]['dominant_emotion']
        except Exception as e:
            print(f"Emotion detection failed: {e}")
            return None

    def save_to_csv(self, name: str, emotion: str, timestamp: str):
        """Save the recognized face's name, detected emotion, and timestamp to a CSV file."""
        file_exists = os.path.isfile(self.csv_file_path)
        with open(self.csv_file_path, mode='a', newline='') as file:
            writer = csv.writer(file)
            if not file_exists:
                writer.writerow(['Name', 'Emotion', 'Time'])
            writer.writerow([name, emotion, timestamp])

    def process_image(self, image_path: str) -> str:
        """Process the image to recognize faces and detect emotions."""
        print(f"Image path: {image_path}")
        print(f"Encoding file path: {self.encoding_file_path}")

        if not os.path.exists(image_path):
            return self.to_json(status="error", message="Image file not found")
        if not os.path.exists(self.encoding_file_path):
            return self.to_json(status="error", message="Encoding file not found")

        frame = cv2.imread(image_path)
        if frame is None:
            return self.to_json(status="error", message="Failed to load image")

        try:
            accuracy_threshold = 0.50
            known_face_encodings, known_face_names = self.load_known_faces()

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
                        emotion = self.detect_emotion(frame)
                        timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
                        if emotion is None:
                            message = f"Hey {best_match_name}, your emotion is not detected. Please try again."
                            self.save_to_csv(best_match_name, "Try Again", timestamp)
                            return self.to_json(status="error", message=message)
                        else:
                            self.save_to_csv(best_match_name, emotion, timestamp)
                            return self.to_json(status="success", name=best_match_name, emotion=emotion, time=timestamp)
                else:
                    return self.to_json(status="error", message="Face not recognized with sufficient accuracy")
        except Exception as e:
            return self.to_json(status="error", message=str(e))

    def to_dict(self, status: str, message: Optional[str] = None, name: Optional[str] = None, emotion: Optional[str] = None, time: Optional[str] = None) -> Dict[str, Any]:
        """Convert the result to a dictionary."""
        return {
            "status": status,
            "message": message,
            "name": name,
            "emotion": emotion,
            "time": time
        }

    def to_json(self, status: str, message: Optional[str] = None, name: Optional[str] = None, emotion: Optional[str] = None, time: Optional[str] = None) -> str:
        """Convert the result to a JSON string."""
        return json.dumps(self.to_dict(status, message, name, emotion, time))

# Ensure the script has this function available for Chaquopy
def process_image(image_path: str, encoding_file_path: str) -> str:
    processor = FaceRecognitionProcessor(encoding_file_path, "/data/user/0/com.example.detectionpython/files/Attendance.csv")
    return processor.process_image(image_path)
