import sys

class PDFBuilder:
    def __init__(self):
        self.pages = []
        self.current_stream = []
        self.page_w = 595.28 # A4 width in pt
        self.page_h = 841.89 # A4 height in pt

    def new_page(self):
        if self.current_stream:
            self.pages.append("\n".join(self.current_stream))
            self.current_stream = []
        return self

    def set_fill(self, r, g, b):
        self.current_stream.append(f"{r:.3f} {g:.3f} {b:.3f} rg")

    def set_stroke(self, r, g, b):
        self.current_stream.append(f"{r:.3f} {g:.3f} {b:.3f} RG")

    def set_line_width(self, w):
        self.current_stream.append(f"{w:.2f} w")

    def line(self, x1, y1, x2, y2):
        self.current_stream.append(f"{x1:.2f} {y1:.2f} m {x2:.2f} {y2:.2f} l S")

    def rounded_rect(self, x, y, w, h, r=4, fill=True, stroke=True):
        if r <= 0:
            op = "B" if fill and stroke else ("f" if fill else "S")
            self.current_stream.append(f"{x:.2f} {y:.2f} {w:.2f} {h:.2f} re {op}")
            return
        k = 0.55228475 * r
        s = [
            f"{x + r:.2f} {y:.2f} m",
            f"{x + w - r:.2f} {y:.2f} l",
            f"{x + w - r + k:.2f} {y:.2f} {x + w:.2f} {y + r - k:.2f} {x + w:.2f} {y + r:.2f} c",
            f"{x + w:.2f} {y + h - r:.2f} l",
            f"{x + w:.2f} {y + h - r + k:.2f} {x + w - r + k:.2f} {y + h:.2f} {x + w - r:.2f} {y + h:.2f} c",
            f"{x + r:.2f} {y + h:.2f} l",
            f"{x + r - k:.2f} {y + h:.2f} {x:.2f} {y + h - r + k:.2f} {x:.2f} {y + h - r:.2f} c",
            f"{x:.2f} {y + r:.2f} l",
            f"{x:.2f} {y + r - k:.2f} {x + r - k:.2f} {y:.2f} {x + r:.2f} {y:.2f} c",
            "h"
        ]
        op = "B" if fill and stroke else ("f" if fill else "S")
        s.append(op)
        self.current_stream.append(" ".join(s))

    def escape_text(self, txt):
        return txt.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")

    def draw_text(self, x, y, text, font="F1", size=10):
        esc = self.escape_text(text)
        self.current_stream.append(f"BT /{font} {size:.2f} Tf {x:.2f} {y:.2f} Td ({esc}) Tj ET")

    def build_pdf(self):
        if self.current_stream:
            self.pages.append("\n".join(self.current_stream))

        # Fonts: F1 = Helvetica, F2 = Helvetica-Bold, F3 = Helvetica-Oblique
        num_pages = len(self.pages)
        objects = []
        
        # 1: Catalog
        # 2: Pages
        # 3.. 3+num_pages-1: Page objects
        # Next: Font objects
        # Next: Content stream objects

        page_obj_ids = [3 + i for i in range(num_pages)]
        next_id = 3 + num_pages

        font_f1 = next_id; next_id += 1
        font_f2 = next_id; next_id += 1
        font_f3 = next_id; next_id += 1

        stream_ids = []
        for _ in range(num_pages):
            stream_ids.append(next_id)
            next_id += 1

        # Catalog
        cat = f"<< /Type /Catalog /Pages 2 0 R >>"
        # Pages
        kids_str = " ".join([f"{pid} 0 R" for pid in page_obj_ids])
        pages_obj = f"<< /Type /Pages /Kids [{kids_str}] /Count {num_pages} >>"

        obj_map = {
            1: cat,
            2: pages_obj
        }

        for i in range(num_pages):
            pid = page_obj_ids[i]
            sid = stream_ids[i]
            page_data = (
                f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 {self.page_w:.2f} {self.page_h:.2f}] "
                f"/Resources << /Font << /F1 {font_f1} 0 R /F2 {font_f2} 0 R /F3 {font_f3} 0 R >> >> "
                f"/Contents {sid} 0 R >>"
            )
            obj_map[pid] = page_data

        obj_map[font_f1] = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"
        obj_map[font_f2] = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"
        obj_map[font_f3] = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Oblique /Encoding /WinAnsiEncoding >>"

        for i in range(num_pages):
            sid = stream_ids[i]
            content = self.pages[i].encode("latin-1", "replace")
            stream_obj = (
                f"<< /Length {len(content)} >>\nstream\n".encode("ascii") +
                content +
                b"\nendstream"
            )
            obj_map[sid] = stream_obj

        # Assemble file
        out = bytearray(b"%PDF-1.4\n")
        offsets = {}
        max_id = max(obj_map.keys())

        for obj_id in range(1, max_id + 1):
            offsets[obj_id] = len(out)
            val = obj_map[obj_id]
            out.extend(f"{obj_id} 0 obj\n".encode("ascii"))
            if isinstance(val, str):
                out.extend(val.encode("latin-1", "replace"))
            else:
                out.extend(val)
            out.extend(b"\nendobj\n")

        xref_offset = len(out)
        out.extend(f"xref\n0 {max_id + 1}\n".encode("ascii"))
        out.extend(b"0000000000 65535 f \n")
        for obj_id in range(1, max_id + 1):
            out.extend(f"{offsets[obj_id]:010d} 00000 n \n".encode("ascii"))

        out.extend(f"trailer\n<< /Size {max_id + 1} /Root 1 0 R >>\nstartxref\n{xref_offset}\n%%EOF\n".encode("ascii"))
        return bytes(out)

builder = PDFBuilder()
builder.new_page()
builder.set_fill(0.1, 0.2, 0.4)
builder.rounded_rect(40, 750, 515, 50, r=8, fill=True, stroke=False)
builder.set_fill(1, 1, 1)
builder.draw_text(60, 770, "Testing PDF Builder", font="F2", size=18)

pdf_bytes = builder.build_pdf()
with open("build/test_builder.pdf", "wb") as f:
    f.write(pdf_bytes)

print("Builder successful, length:", len(pdf_bytes))
