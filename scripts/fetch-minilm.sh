#!/usr/bin/env bash
# Fetch the local MiniLM embedding model (all-MiniLM-L6-v2, ONNX export) from Hugging Face.
#
# The model (~86 MB) is deliberately not committed — models/ is gitignored. It is only
# needed when running with EMBEDDING_PROVIDER=minilm; the default provider is the in-repo
# deterministic fake, which needs nothing external. Without the files, selecting the
# minilm provider fails fast at startup and points here.
set -euo pipefail

cd "$(dirname "$0")/.."
mkdir -p models/all-MiniLM-L6-v2

base=https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main
curl -fL --progress-bar -o models/all-MiniLM-L6-v2/model.onnx "$base/onnx/model.onnx"
curl -fL --progress-bar -o models/all-MiniLM-L6-v2/tokenizer.json "$base/tokenizer.json"

echo "Model ready under models/all-MiniLM-L6-v2/"
