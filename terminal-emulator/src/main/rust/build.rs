fn main() {
    // When building with cargo-ndk for Android, this script runs.
    // No special build steps needed for now - pure Rust.
    println!("cargo:rerun-if-changed=src/");
}
