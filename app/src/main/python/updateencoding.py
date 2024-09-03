import os
import face_recognition
import pickle
import json
import cv2
import numpy as np

def liveness_check(face_image) -> bool:
    """Basic liveness check based on color variance (simple heuristic)."""
    gray = cv2.cvtColor(face_image, cv2.COLOR_BGR2GRAY)
    variance = cv2.Laplacian(gray, cv2.CV_64F).var()
    print(f"Liveness check variance: {variance}")

    # You can adjust the threshold based on empirical data or testing
    threshold = 950.0
    return variance > threshold

def load_face_data(face_data_file):
    """Load the face data containing encodings, names, and IDs from the face_data.pkl file."""
    if os.path.exists(face_data_file) and os.path.getsize(face_data_file) > 0:
        with open(face_data_file, 'rb') as file:
            face_data = pickle.load(file)
            return face_data
    else:
        return {"encodings": [], "names": [], "ids": []}  # Return empty data if the file doesn't exist

def save_face_data_to_file(face_data, face_data_file):
    """Save the face data containing encodings, names, and IDs to the face_data.pkl file."""
    with open(face_data_file, 'wb') as file:
        pickle.dump(face_data, file)
    print(f"Data saved to {face_data_file}")

def update_face_encodings(new_photo_path, face_data_file, user_id: Optional[int] = None):
    response = {'message': None}  # Default message is set to None

    # Load existing face data
    face_data = load_face_data(face_data_file)
    print(f"Loaded face_data: {len(face_data['encodings'])} faces")

    # Check if the new photo path is valid
    if not os.path.isfile(new_photo_path):
        response['message'] = f"File does not exist: {new_photo_path}"
        return json.dumps(response)

    if os.path.getsize(new_photo_path) == 0:
        response['message'] = f"File is empty: {new_photo_path}"
        return json.dumps(response)

    # Add new photo
    print(f"Adding new photo: {new_photo_path}")
    image = face_recognition.load_image_file(new_photo_path)
    encodings = face_recognition.face_encodings(image)

    if encodings:
        for encoding in encodings:
            # Compare faces and calculate distances
            distances = face_recognition.face_distance(face_data['encodings'], encoding)
            if distances.size > 0:
                min_distance = min(distances)
                min_distance_index = np.argmin(distances)
                # Calculate accuracy as 1 - distance
                accuracy = 1 - min_distance

                if accuracy >= 0.5:
                    matched_id = face_data['ids'][min_distance_index]
                    response['message'] = f"The face is enrolled against another ID: {matched_id}"
                    break  # Break the loop if a match is found
                else:
                    if user_id is not None:
                        if user_id in face_data["ids"]:
                            response['message'] = f"ID {user_id} already exists."
                            break  # Stop further processing if ID already exists
                        else:
                            # Use provided ID
                            current_id = user_id
                            face_data["encodings"].append(encoding)
                            face_data["names"].append(os.path.splitext(os.path.basename(new_photo_path))[0])
                            face_data["ids"].append(current_id)
                            print(f"Encoded face from {new_photo_path}, assigned ID: {current_id}")
                            response['message'] = "Registration Successfully"
                    else:
                        # Generate new ID if user_id is not provided
                        current_id = max(face_data["ids"], default=0) + 1
                        face_data["encodings"].append(encoding)
                        face_data["names"].append(os.path.splitext(os.path.basename(new_photo_path))[0])
                        face_data["ids"].append(current_id)
                        print(f"Encoded face from {new_photo_path}, assigned ID: {current_id}")
                        response['message'] = "Registration Successfully"
    else:
        response['message'] = "No faces found in the image, please try again"

    # Save updated face data to face_data.pkl
    save_face_data_to_file(face_data, face_data_file)
    return json.dumps(response)  # Return the response as JSON

# Example usage
face_data_file = "face_data.pkl"  # File for storing face data with IDs
new_photo_path = "C:/Users/user/Downloads/DB/osama.png"  # Path to the new photo for registration
user_id = 123  # User-specified ID for registration

print(update_face_encodings(new_photo_path, face_data_file, user_id))
