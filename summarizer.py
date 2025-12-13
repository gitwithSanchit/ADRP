import os
import sys
import io

# ---------------------------------------------------------
# 1. SYSTEM CONFIGURATION (Speed & Stability)
# ---------------------------------------------------------
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'
os.environ['TF_ENABLE_ONEDNN_OPTS'] = '0'
sys.stderr = open(os.devnull, 'w')

# Force standard UTF-8 encoding to prevent special character crashes
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sys.stdin = io.TextIOWrapper(sys.stdin.buffer, encoding='utf-8')

try:
    from transformers import pipeline, logging
    logging.set_verbosity_error()
except ImportError:
    sys.exit(1)

# ---------------------------------------------------------
# 2. MODEL: DistilBART (Fast & Capable)
# ---------------------------------------------------------
# 'sshleifer/distilbart-cnn-12-6' is the industry standard for fast CPU summarization.
MODEL_NAME = "sshleifer/distilbart-cnn-12-6"
summarizer = pipeline("summarization", model=MODEL_NAME)

def clean_text(text):
    """Removes excessive newlines and spaces for better AI processing."""
    if not text: return ""
    # Replace multiple spaces/newlines with a single space
    return " ".join(text.split())

def generate_summary(text, target_chunks):
    if not text or len(text.strip()) == 0:
        return "No text provided."

    # Pre-clean text to maximize the useful content in each chunk
    cleaned_text = clean_text(text)

    # A. CHUNKING
    # 2800 chars is the "Safe Zone" for DistilBART (approx 700 tokens)
    chunk_size = 2800
    all_chunks = [cleaned_text[i:i+chunk_size] for i in range(0, len(cleaned_text), chunk_size)]

    selected_chunks = []

    # B. INTELLIGENT SELECTION (Story Arc Mode)
    if len(all_chunks) > target_chunks:
        # Calculate mathematical step to skip boring parts
        step = (len(all_chunks) - 1) / max(1, target_chunks - 1)

        indices = []
        for i in range(target_chunks):
            idx = int(i * step)
            # Ensure we don't go out of bounds
            if idx < len(all_chunks):
                indices.append(idx)

        # Always force the Conclusion (Last Chunk)
        if len(all_chunks) - 1 not in indices:
            indices[-1] = len(all_chunks) - 1

        indices = sorted(list(set(indices)))
        selected_chunks = [all_chunks[i] for i in indices]
    else:
        selected_chunks = all_chunks

    # C. GENERATION LOOP
    final_summary_parts = []

    for chunk in selected_chunks:
        try:
            # Tuned Parameters for "Professional Detail":
            # max_length=230: Allows long, detailed paragraphs.
            # min_length=60: Prevents "too short" summaries.
            # num_beams=2: smarter search (better grammar).
            # no_repeat_ngram_size=3: Prevents repetitive phrases.
            output = summarizer(
                chunk,
                max_length=230,
                min_length=60,
                do_sample=False,
                truncation=True,
                num_beams=2,
                no_repeat_ngram_size=3
            )
            text_segment = output[0]['summary_text']
            final_summary_parts.append(text_segment)
        except Exception:
            continue

    # Join with double newlines so the UI renders them as distinct paragraphs
    return "\n\n".join(final_summary_parts)

# ---------------------------------------------------------
# 4. ENTRY POINT
# ---------------------------------------------------------
if __name__ == "__main__":
    try:
        # Default to 3 chunks if argument is missing
        target_chunk_count = 3
        if len(sys.argv) > 1:
            try:
                target_chunk_count = int(sys.argv[1])
            except ValueError:
                pass

        input_content = sys.stdin.read()

        # Run Logic
        summary = generate_summary(input_content, target_chunk_count)

        # Print Result (Standard Output goes back to Java)
        print(summary)

    except Exception:
        # Fail gracefully
        print("")