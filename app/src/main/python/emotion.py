import cv2
import numpy as np
import face_recognition
import pickle
from datetime import datetime
import os
import json
from typing import Optional, Dict, Any

def load_known_faces(encoding_file_path: str):
    """Load known faces and their names from the encoding file."""
    if os.path.exists(encoding_file_path):
        try:
            with open(encoding_file_path, 'rb') as file:
                known_face_encodings, known_face_names = pickle.load(file)
                print(f"Loaded {len(known_face_encodings)} encodings from file.")
        except Exception as e:
            print(f"Failed to load encodings: {e}")
            known_face_encodings = []
            known_face_names = []
    else:
        print(f"Encoding file not found at: {encoding_file_path}")
        known_face_encodings = []
        known_face_names = []

    return known_face_encodings, known_face_names


def to_dict(status: str, message: Optional[str] = None, attendance_time: Optional[str] = None) -> Dict[str, Any]:
    """Convert the result to a dictionary."""
    return {
        "status": status,
        "message": message,
        "attendance_time": attendance_time
    }

def to_json(status: str, message: Optional[str] = None, attendance_time: Optional[str] = None) -> str:
    """Convert the result to a JSON string."""
    return json.dumps(to_dict(status, message, attendance_time))


def process_image(image_path, encoding_file_path):
    """Process the image to recognize faces and return attendance time."""
    print(f"Processing image at path: {image_path}")
    print(f"Using encoding file at path: {encoding_file_path}")

    # Initialize response variables
    status = "error"
    message = None
    attendance_time = None

    if not os.path.exists(image_path):
        message = "Image file not found"
        print(message)
    elif not os.path.exists(encoding_file_path):
        message = "Encoding file not found"
        print(message)
    else:
        frame = cv2.imread(image_path)
        if frame is None:
            message = "Failed to load image"
            print(message)
        else:
            try:
                accuracy_threshold = 0.50
                known_face_encodings, known_face_names = load_known_faces(encoding_file_path)

                if not known_face_encodings or not known_face_names:
                    message = "No known faces in the system"
                    print(message)
                else:
                    face_locations = face_recognition.face_locations(frame)
                    face_encodings = face_recognition.face_encodings(frame, face_locations)

                    if not face_encodings:
                        message = "No face detected in the image"
                        print(message)
                    else:
                        face_found = False
                        for (top, right, bottom, left), face_encoding in zip(face_locations, face_encodings):
                            matches = face_recognition.compare_faces(known_face_encodings, face_encoding)
                            distances = face_recognition.face_distance(known_face_encodings, face_encoding)

                            if any(matches):
                                face_found = True
                                best_match_index = np.argmin(distances)
                                best_match_name = known_face_names[best_match_index]
                                best_match_accuracy = distances[best_match_index]

                                if best_match_accuracy < accuracy_threshold:
                                    # Get current time (hours:minutes:seconds)
                                    current_time = datetime.now().strftime("%H:%M:%S")
                                    attendance_time = current_time
                                    hour = int(datetime.now().strftime("%H"))

                                    if hour < 12:
                                        message = f"Good morning {best_match_name}"
                                    else:
                                        message = f"Good evening {best_match_name}"

                                    print(f"Time Attendance: {attendance_time}")
                                    print(f"Match found: {best_match_name} with accuracy {best_match_accuracy}")
                                    status = "success"
                                    break  # Exit the loop as we found a match
                                else:
                                    message = "Face recognized but accuracy is below threshold"
                                    print(message)
                                    break
                            else:
                                message = "Face not recognized"
                                print(message)
                                break

            except Exception as e:
                message = f"Exception occurred: {str(e)}"
                print(message)

    return json.dumps({
        "status": status,
        "message": message,
        "attendance_time": attendance_time
    })


# Define paths to the required files (ensure these paths are accessible in Android environment)
def get_file_paths():
    """Retrieve file paths for image and encoding file."""
    # Adjust paths as necessary for the Android environment
    image_path = '/data/user/0/com.example.detectionpython/cache/temp_image_resized.jpg'  # Example path
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
