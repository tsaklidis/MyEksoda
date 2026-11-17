#!/usr/bin/env python3
"""
Eksoda Encrypted Backup Decryptor & Inspector

This utility script allows users to decrypt and inspect their Eksoda backup files
independently of the Android application, ensuring data recoverability and ownership.

Requirements:
    pip install cryptography

Usage:
    python3 decrypt_bu.py backup.eksoda [output.json]
"""

import base64
import getpass
import io
import json
import os
import struct
import sys
import unicodedata
import zipfile
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC

def print_banner():
    print("=" * 60)
    print("       Eksoda Encrypted Backup Decryptor & Inspector")
    print("=" * 60)

def decrypt_backup(input_path: str, output_path: str, passphrase: str):
    if not os.path.exists(input_path):
        print(f"[-] Error: Backup file not found: '{input_path}'", file=sys.stderr)
        sys.exit(1)

    print(f"[i] Reading backup file: {input_path}...")
    with open(input_path, "rb") as f:
        data = f.read()

    # 1. Verify magic bytes
    magic = data[:8]
    if magic != b"EKSODABK":
        print("[-] Error: Not a valid Eksoda backup file (incorrect magic bytes).", file=sys.stderr)
        sys.exit(1)

    # 2. Read header length & header JSON
    if len(data) < 12:
        print("[-] Error: File is too small to be a valid backup.", file=sys.stderr)
        sys.exit(1)

    header_length = struct.unpack(">I", data[8:12])[0]
    if len(data) < 12 + header_length:
        print("[-] Error: Backup file is truncated or corrupted.", file=sys.stderr)
        sys.exit(1)

    header_bytes = data[12:12 + header_length]
    ciphertext = data[12 + header_length:]

    try:
        header_json = json.loads(header_bytes.decode("utf-8"))
    except Exception as e:
        print(f"[-] Error: Failed to parse backup header JSON: {e}", file=sys.stderr)
        sys.exit(1)

    format_ver = header_json.get("format", 1)
    if format_ver > 1:
        print(f"[-] Error: Unsupported backup format version ({format_ver}).", file=sys.stderr)
        sys.exit(1)

    kdf = header_json.get("kdf", {})
    cipher_info = header_json.get("cipher", {})

    if kdf.get("name") != "PBKDF2-HMAC-SHA256" or cipher_info.get("name") != "AES-256-GCM":
        print("[-] Error: Unsupported cryptographic algorithm in backup header.", file=sys.stderr)
        sys.exit(1)

    iterations = kdf.get("iterations", 600000)
    try:
        salt = base64.b64decode(kdf["salt"])
        nonce = base64.b64decode(cipher_info["nonce"])
    except Exception as e:
        print(f"[-] Error: Invalid salt or nonce encoding in header: {e}", file=sys.stderr)
        sys.exit(1)

    print(f"[i] Backup Metadata:")
    print(f"    - App Version    : {header_json.get('app_version', 'Unknown')}")
    print(f"    - Created At     : {header_json.get('created_at', 'Unknown')}")
    print(f"    - KDF Iterations : {iterations:,}")

    print("[i] Deriving decryption key from passphrase...")
    normalized_passphrase = unicodedata.normalize("NFC", passphrase)

    kdf_obj = PBKDF2HMAC(
        algorithm=hashes.SHA256(),
        length=32,
        salt=salt,
        iterations=iterations,
    )
    key = kdf_obj.derive(normalized_passphrase.encode("utf-8"))

    print("[i] Decrypting payload (AES-256-GCM)...")
    aesgcm = AESGCM(key)
    try:
        plaintext = aesgcm.decrypt(nonce, ciphertext, header_bytes)
    except Exception:
        print("[-] Error: Decryption failed!", file=sys.stderr)
        print("    This usually means the password (passphrase) was incorrect, or the backup file was modified.", file=sys.stderr)
        sys.exit(1)

    print("[+] Decryption successful!")

    # Write decrypted payload (which is a ZIP archive containing the SQLite database / export)
    with open(output_path, "wb") as f:
        f.write(plaintext)

    print(f"[+] Decrypted archive written to: {output_path}")

    # Inspect contents
    try:
        with zipfile.ZipFile(io.BytesIO(plaintext)) as zf:
            print("\n[i] Archive Contents:")
            for info in zf.infolist():
                print(f"    - {info.filename} ({info.file_size:,} bytes)")
    except Exception:
        pass

def main():
    print_banner()
    if len(sys.argv) < 2:
        print("Usage: python3 decrypt_bu.py <backup_file.eksoda> [output_archive.zip]")
        sys.exit(1)

    input_path = sys.argv[1]
    output_path = sys.argv[2] if len(sys.argv) > 2 else input_path + ".unlocked.zip"

    passphrase = getpass.getpass("Enter backup password (passphrase): ")
    decrypt_backup(input_path, output_path, passphrase)

if __name__ == "__main__":
    main()
