import cv2
import numpy as np
import face_recognition
import pickle
from datetime import datetime
from deepface import DeepFace
import os
import csv

def load_known_faces(encoding_file_path):
    if os.path.exists(encoding_file_path):
        with open(encoding_file_path, 'rb') as file:
            known_face_encodings, known_face_names = pickle.load(file)
    else:
        known_face_encodings = []
        known_face_names = []

    return known_face_encodings, known_face_names

def detect_emotion(frame):
    try:
        temp_path = 'temp_frame.jpg'
        cv2.imwrite(temp_path, frame)
        emotion_results = DeepFace.analyze(img_path=temp_path, actions=['emotion'])
        os.remove(temp_path)
        return emotion_results[0]['dominant_emotion']
    except Exception as e:
        print(f"Emotion detection failed: {e}")
        return None

def save_to_csv(name, emotion, timestamp, csv_file='Attendance.csv'):
    file_exists = os.path.isfile(csv_file)
    with open(csv_file, mode='a', newline='') as file:
        writer = csv.writer(file)
        if not file_exists:
            writer.writerow(['Name', 'Emotion', 'Time'])
        writer.writerow([name, emotion, timestamp])

def process_image(image_path, encoding_file_path):
    import os

    def process_image(image_path, encoding_file_path):
        # Debugging output
        print(f"Image path: {image_path}")
        print(f"Encoding file path: {encoding_file_path}")

        # Check if files exist
        if not os.path.exists(image_path):
            print("Error: Image file does not exist")
            return {"status": "error", "message": "Image file not found"}
        if not os.path.exists(encoding_file_path):
            print("Error: Encoding file does not exist")
            return {"status": "error", "message": "Encoding file not found"}

        # Continue with processing if files exist
        # ...


    accuracy_threshold = 0.50
    known_face_encodings, known_face_names = load_known_faces(encoding_file_path)

    frame = cv2.imread(image_path)
    if frame is None:
        print("Failed to load image")
        return {"status": "error", "message": "Failed to load image"}

    try:
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
                        save_to_csv(best_match_name, "Try Again", timestamp)
                        return {"status": "error", "message": message}
                    else:
                        save_to_csv(best_match_name, emotion, timestamp)
                        return {"status": "success", "name": best_match_name, "emotion": emotion, "time": timestamp}
                else:
                    return {"status": "error", "message": "Face not recognized with sufficient accuracy"}
    except Exception as e:
        print(f"Image processing failed: {e}")
        return {"status": "error", "message": str(e)}


# Example usage
# Adjust the paths according to your environment
encoding_file_path = '/path/to/encodings.pkl'  # Replace with actual path
image_path = '/path/to/temp_image.jpg'  # Replace with actual path
result = process_image(image_path, encoding_file_path)
print(result)