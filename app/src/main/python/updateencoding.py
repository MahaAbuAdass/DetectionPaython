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
    threshold = 10.0
    return variance > threshold

def update_face_encodings(new_photo_path, encoding_file):
    print("encoding file", encoding_file)
    response = {'message': None}  # Default message is set to None

    # Initialize the encodings and names lists
    known_face_encodings = []
    known_face_names = []

    # Load existing encodings if the file exists and is not empty
    if os.path.exists(encoding_file) and os.path.getsize(encoding_file) > 0:
        print("Loading existing encodings...")
        with open(encoding_file, 'rb') as file:
            known_face_encodings, known_face_names = pickle.load(file)
        print(f"Number of known faces: {len(known_face_names)}")
    else:
        print("Encoding file is missing or empty. Starting fresh.")

    # Check if the new photo path is valid
    if not os.path.isfile(new_photo_path):
        response['message'] = f"File does not exist: {new_photo_path}"
        return json.dumps(response)

    # Check if the new photo file is empty
    if os.path.getsize(new_photo_path) == 0:
        response['message'] = f"File is empty: {new_photo_path}"
        return json.dumps(response)

    # Add new photo
    print(f"Adding new photo: {new_photo_path}")
    image = face_recognition.load_image_file(new_photo_path)
    encodings = face_recognition.face_encodings(image)

    if encodings:
        for encoding in encodings:
            # Extract the upper part of the face for liveness check
            top, right, bottom, left = face_recognition.face_locations(image)[0]
            upper_face = image[top:top + int((bottom - top) / 2), left:right]

            if liveness_check(upper_face):
                known_face_encodings.append(encoding)
                known_face_names.append(os.path.splitext(os.path.basename(new_photo_path))[0])
                print(f"Encoded face from {new_photo_path}")
                response['message'] = f"Encoded and added face from {new_photo_path}"
            else:
                response['message'] = "Liveness check failed: Fake face detected"
                print(response['message'])
                break  # Exit if liveness check fails
    else:
        response['message'] = "No faces found in the image, please try again"

    # Save updated encodings to file
    with open(encoding_file, 'wb') as file:
        pickle.dump((known_face_encodings, known_face_names), file)

    if encodings:
        response['message'] = "Registration Successfully"

    return json.dumps(response)  # Return the response as JSON

def get_image_path_from_encoding(encoding, encoding_file):
    response = {'image_path': None}

    # Load existing encodings if the file exists and is not empty
    if os.path.exists(encoding_file) and os.path.getsize(encoding_file) > 0:
        print("Loading existing encodings...")
        with open(encoding_file, 'rb') as file:
            known_face_encodings, known_face_names = pickle.load(file)
    else:
        print("Encoding file is missing or empty.")
        return json.dumps(response)

    # Find the image path corresponding to the encoding
    for idx, enc in enumerate(known_face_encodings):
        if face_recognition.compare_faces([enc], encoding)[0]:
            response['image_path'] = known_face_names[idx]
            break

    return json.dumps(response)