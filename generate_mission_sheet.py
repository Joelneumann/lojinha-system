import os
import sys

class PDFCanvas:
    def __init__(self, filename="User_Test_Mission_Sheet.pdf"):
        self.filename = filename
        self.pages = []
        self.current_stream = []
        self.page_w = 595.28  # A4 width
        self.page_h = 841.89  # A4 height

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

    def rect(self, x, y, w, h, fill=True, stroke=True):
        op = "B" if fill and stroke else ("f" if fill else "S")
        self.current_stream.append(f"{x:.2f} {y:.2f} {w:.2f} {h:.2f} re {op}")

    def rounded_rect(self, x, y, w, h, r=5, fill=True, stroke=True):
        if r <= 0:
            self.rect(x, y, w, h, fill=fill, stroke=stroke)
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

    def text(self, x, y, text_str, font="F1", size=10):
        esc = self.escape_text(text_str)
        self.current_stream.append(f"BT /{font} {size:.2f} Tf {x:.2f} {y:.2f} Td ({esc}) Tj ET")

    def draw_badge(self, x, y, text_str, bg_rgb=(0.06, 0.09, 0.16), text_rgb=(1, 1, 1), font_size=8, padding_h=6, height=14):
        w = len(text_str) * font_size * 0.62 + padding_h * 2
        self.set_fill(*bg_rgb)
        self.rounded_rect(x, y - 2, w, height, r=3, fill=True, stroke=False)
        self.set_fill(*text_rgb)
        self.text(x + padding_h, y + 2, text_str, font="F2", size=font_size)
        return w

    def draw_checkbox(self, x, y, size=9):
        self.set_fill(1, 1, 1)
        self.set_stroke(0.4, 0.46, 0.55)
        self.set_line_width(1.0)
        self.rounded_rect(x, y - 1, size, size, r=2, fill=True, stroke=True)

    def draw_step(self, x, y, checkbox=True, bold_title="", text="", font_size=9):
        cb_offset = 16 if checkbox else 0
        if checkbox:
            self.draw_checkbox(x, y + 1, size=9)

        cur_x = x + cb_offset
        if bold_title:
            self.set_fill(0.06, 0.09, 0.16)
            self.text(cur_x, y, bold_title + " ", font="F2", size=font_size)
            title_w = len(bold_title + " ") * font_size * 0.58
            cur_x += title_w

        self.set_fill(0.12, 0.16, 0.23)
        self.text(cur_x, y, text, font="F1", size=font_size)

    def compile(self):
        if self.current_stream:
            self.pages.append("\n".join(self.current_stream))

        num_pages = len(self.pages)
        page_obj_ids = [3 + i for i in range(num_pages)]
        next_id = 3 + num_pages

        font_f1 = next_id; next_id += 1  # Helvetica
        font_f2 = next_id; next_id += 1  # Helvetica-Bold
        font_f3 = next_id; next_id += 1  # Helvetica-Oblique

        stream_ids = []
        for _ in range(num_pages):
            stream_ids.append(next_id)
            next_id += 1

        cat = f"<< /Type /Catalog /Pages 2 0 R >>"
        kids_str = " ".join([f"{pid} 0 R" for pid in page_obj_ids])
        pages_obj = f"<< /Type /Pages /Kids [{kids_str}] /Count {num_pages} >>"

        obj_map = {1: cat, 2: pages_obj}

        for i in range(num_pages):
            pid = page_obj_ids[i]
            sid = stream_ids[i]
            obj_map[pid] = (
                f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 {self.page_w:.2f} {self.page_h:.2f}] "
                f"/Resources << /Font << /F1 {font_f1} 0 R /F2 {font_f2} 0 R /F3 {font_f3} 0 R >> >> "
                f"/Contents {sid} 0 R >>"
            )

        obj_map[font_f1] = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"
        obj_map[font_f2] = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"
        obj_map[font_f3] = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Oblique /Encoding /WinAnsiEncoding >>"

        for i in range(num_pages):
            sid = stream_ids[i]
            content = self.pages[i].encode("latin-1", "replace")
            obj_map[sid] = (
                f"<< /Length {len(content)} >>\nstream\n".encode("ascii") +
                content +
                b"\nendstream"
            )

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

        with open(self.filename, "wb") as f:
            f.write(bytes(out))
        print(f"Wrote {len(out)} bytes to {self.filename}")


def generate_mission_sheet():
    c = PDFCanvas("User_Test_Mission_Sheet.pdf")

    # =========================================================================
    # PAGE 1
    # =========================================================================
    c.new_page()

    # Top Brand Header Banner (Dark Navy)
    c.set_fill(0.059, 0.090, 0.165)
    c.rounded_rect(36, 762, 523, 58, r=6, fill=True, stroke=False)

    c.set_fill(1, 1, 1)
    c.text(48, 800, "LOJINHA POS  -  USER USABILITY TEST MISSION", font="F2", size=15)
    c.set_fill(0.85, 0.90, 0.98)
    c.text(48, 786, "End-to-End Hands-on Evaluation Sheet for Kiosk Testers", font="F1", size=9.5)
    c.draw_badge(462, 794, "TEST SCRIPT", bg_rgb=(0.145, 0.388, 0.922), text_rgb=(1, 1, 1), font_size=8.5, padding_h=8, height=16)

    # Metadata Field Bar (Under Header)
    c.set_fill(0.96, 0.97, 0.98)
    c.set_stroke(0.85, 0.88, 0.92)
    c.set_line_width(0.75)
    c.rounded_rect(36, 726, 523, 26, r=4, fill=True, stroke=True)

    c.set_fill(0.39, 0.45, 0.54)
    c.text(46, 735, "Tester Name:", font="F2", size=8.5)
    c.set_stroke(0.7, 0.75, 0.82)
    c.line(105, 733, 235, 733)

    c.text(250, 735, "Date:", font="F2", size=8.5)
    c.line(275, 733, 375, 733)

    c.text(390, 735, "Terminal / Station:", font="F2", size=8.5)
    c.line(472, 733, 545, 733)

    # Welcome & Instructions Card (Light Blue Container)
    c.set_fill(0.945, 0.965, 0.99)
    c.set_stroke(0.75, 0.82, 0.93)
    c.set_line_width(0.75)
    c.rounded_rect(36, 650, 523, 66, r=5, fill=True, stroke=True)

    c.set_fill(0.06, 0.15, 0.35)
    c.text(46, 700, "Welcome to the Community Kiosk!", font="F2", size=10)
    c.set_fill(0.18, 0.24, 0.35)
    c.text(46, 686, "You are a community member using an offline self-service store with a prepaid account balance.", font="F1", size=8.8)
    c.text(46, 673, "Please perform the tasks below naturally at your own pace. Important: We are testing the software,", font="F1", size=8.8)
    c.text(46, 660, "NOT you. Speak your thoughts aloud and note any hesitation, confusion, or delight.", font="F1", size=8.8)

    # -------------------------------------------------------------------------
    # SCENARIO 1 CARD (Security & Personalization)
    # -------------------------------------------------------------------------
    card1_y = 502
    c.set_fill(0.985, 0.988, 0.995)
    c.set_stroke(0.82, 0.85, 0.90)
    c.set_line_width(0.75)
    c.rounded_rect(36, card1_y, 523, 138, r=5, fill=True, stroke=True)

    # Header in Card 1
    c.draw_badge(46, card1_y + 116, "PHASE 1", bg_rgb=(0.06, 0.15, 0.35), text_rgb=(1, 1, 1), font_size=8, padding_h=6, height=14)
    c.set_fill(0.06, 0.09, 0.16)
    c.text(98, card1_y + 118, "Account Discovery & Security Personalization", font="F2", size=11)

    c.set_stroke(0.88, 0.90, 0.94)
    c.line(46, card1_y + 110, 545, card1_y + 110)

    # Steps in Card 1
    s1_y = card1_y + 92
    c.draw_step(48, s1_y, checkbox=True, bold_title="1. Locate & Select Profile:",
                text="Find your profile on the main kiosk screen (type name or scan card). Open it.")
    s1_y -= 18
    c.draw_step(48, s1_y, checkbox=True, bold_title="2. Open Account Settings:",
                text="Navigate to your user settings via the History / Account button.")
    s1_y -= 18
    c.draw_step(48, s1_y, checkbox=True, bold_title="3. Configure 4-Digit PIN:",
                text="Set a 4-digit PIN code to secure your account against accidental purchases.")
    s1_y -= 18
    c.draw_step(48, s1_y, checkbox=True, bold_title="4. Personalize Avatar:",
                text="Switch to Emoji avatar, select your favorite emoji icon and choose a card color.")
    s1_y -= 18
    c.draw_step(48, s1_y, checkbox=True, bold_title="5. Set Language & Currency:",
                text="Choose your preferred language (DE / EN / BR) and USD/EUR helper. Save & Logout.")
    s1_y -= 18
    c.draw_step(48, s1_y, checkbox=True, bold_title="6. Verify Access Gate:",
                text="Click your user card again from the main screen. Enter your new PIN to log in.")

    # -------------------------------------------------------------------------
    # SCENARIO 2 CARD (Shopping, 1,5 kg weight -> Change to 750 g)
    # -------------------------------------------------------------------------
    card2_y = 310
    c.set_fill(0.985, 0.988, 0.995)
    c.set_stroke(0.82, 0.85, 0.90)
    c.set_line_width(0.75)
    c.rounded_rect(36, card2_y, 523, 180, r=5, fill=True, stroke=True)

    # Header in Card 2
    c.draw_badge(46, card2_y + 158, "PHASE 2", bg_rgb=(0.02, 0.58, 0.41), text_rgb=(1, 1, 1), font_size=8, padding_h=6, height=14)
    c.set_fill(0.06, 0.09, 0.16)
    c.text(98, card2_y + 160, "Shopping, Weighted Goods & In-Cart Weight Modification", font="F2", size=11)

    c.set_stroke(0.88, 0.90, 0.94)
    c.line(46, card2_y + 152, 545, card2_y + 152)

    # Steps in Card 2
    s2_y = card2_y + 134
    c.draw_step(48, s2_y, checkbox=True, bold_title="1. Add Packaged Item:",
                text="Search or scan a packaged drink/snack (e.g. Club Mate). Add 2 units.")
    s2_y -= 18
    c.draw_step(48, s2_y, checkbox=True, bold_title="2. Reduce Quantity in Cart:",
                text="In the cart panel on the right, use the '-' button to change quantity to 1 unit.")
    s2_y -= 18
    c.draw_step(48, s2_y, checkbox=True, bold_title="3. Add Weighted Goods (1,5 kg):",
                text="Search or click a weighted product (e.g. Apples / Fruit / Bulk).")
    s2_y -= 14
    c.set_fill(0.25, 0.30, 0.38)
    c.text(64, s2_y, "--> When prompted by the weight dialog, type exactly: 1,5 kg (or 1.5) and submit.", font="F3", size=8.5)

    s2_y -= 18
    c.draw_step(48, s2_y, checkbox=True, bold_title="4. Change Weight to 750 gram:",
                text="You changed your mind: 1.5 kg is too heavy, you only want 750 grams!")
    s2_y -= 14
    c.set_fill(0.85, 0.20, 0.20)
    c.text(64, s2_y, "--> Remove or modify the weighted item in the cart, then add it with: 750 g (or 750 gram).", font="F2", size=8.5)

    s2_y -= 18
    c.draw_step(48, s2_y, checkbox=True, bold_title="5. Check Total & Balance After:",
                text="Observe your Cart Total and the projected 'Balance After Purchase'.")
    s2_y -= 18
    c.draw_step(48, s2_y, checkbox=True, bold_title="6. Finalize Checkout:",
                text="Click 'Buy / Complete Purchase'. Verify the modal confirmation amount and confirm.")

    # Scenario 2 tester observation callout
    c.set_fill(0.95, 0.98, 0.95)
    c.set_stroke(0.70, 0.85, 0.75)
    c.rounded_rect(48, card2_y + 8, 499, 22, r=3, fill=True, stroke=True)
    c.set_fill(0.05, 0.45, 0.25)
    c.text(56, card2_y + 15, "Tester Check: Did the system convert 1,5 kg and 750 g without syntax complaints?", font="F2", size=8)

    # -------------------------------------------------------------------------
    # SCENARIO 3 CARD (Receipt & Flow)
    # -------------------------------------------------------------------------
    card3_y = 120
    c.set_fill(0.985, 0.988, 0.995)
    c.set_stroke(0.82, 0.85, 0.90)
    c.set_line_width(0.75)
    c.rounded_rect(36, card3_y, 523, 178, r=5, fill=True, stroke=True)

    # Header in Card 3
    c.draw_badge(46, card3_y + 156, "PHASE 3", bg_rgb=(0.85, 0.46, 0.02), text_rgb=(1, 1, 1), font_size=8, padding_h=6, height=14)
    c.set_fill(0.06, 0.09, 0.16)
    c.text(98, card3_y + 158, "Inspecting Audit Receipts & Financial Balance Flow", font="F2", size=11)

    c.set_stroke(0.88, 0.90, 0.94)
    c.line(46, card3_y + 150, 545, card3_y + 150)

    # Steps in Card 3
    s3_y = card3_y + 132
    c.draw_step(48, s3_y, checkbox=True, bold_title="1. View Automatic Receipt:",
                text="Notice that checkout automatically transitioned you to Transaction History.")
    s3_y -= 18
    c.draw_step(48, s3_y, checkbox=True, bold_title="2. Verify Line Item Breakdown:",
                text="Locate your recent purchase. Verify that both the unit item and the 750 g item appear.")
    s3_y -= 18
    c.draw_step(48, s3_y, checkbox=True, bold_title="3. Verify Balance Flow Banner:",
                text="Check the 'Balance: Before -> After' indicator on the transaction card.")
    s3_y -= 18
    c.draw_step(48, s3_y, checkbox=True, bold_title="4. Check Filter & Search:",
                text="Use the type filter buttons (Purchases, Deposits) and search bar to filter entries.")
    s3_y -= 18
    c.draw_step(48, s3_y, checkbox=True, bold_title="5. Return to Shopping Screen:",
                text="Click 'Continue Shopping'. Confirm your active session is maintained seamlessly.")

    # Scenario 3 observation prompt
    c.set_fill(0.96, 0.96, 0.97)
    c.set_stroke(0.85, 0.88, 0.92)
    c.rounded_rect(48, card3_y + 8, 499, 36, r=3, fill=True, stroke=True)
    c.set_fill(0.35, 0.40, 0.50)
    c.text(56, card3_y + 28, "Tester Reflection:", font="F2", size=8)
    c.text(56, card3_y + 16, "Write your balance before purchase: _______________   Balance after purchase: _______________", font="F1", size=8.2)

    # Page 1 Footer
    c.set_stroke(0.85, 0.88, 0.92)
    c.line(36, 50, 559, 50)
    c.set_fill(0.50, 0.55, 0.62)
    c.text(36, 38, "Lojinha Self-Service POS System  *  Usability Evaluation Mission Sheet", font="F1", size=8)
    c.text(480, 38, "Page 1 of 2  >>", font="F2", size=8)

    # =========================================================================
    # PAGE 2
    # =========================================================================
    c.new_page()

    # Top Mini Header
    c.set_fill(0.059, 0.090, 0.165)
    c.rounded_rect(36, 786, 523, 34, r=5, fill=True, stroke=False)
    c.set_fill(1, 1, 1)
    c.text(48, 799, "LOJINHA POS  -  USER USABILITY TEST MISSION", font="F2", size=11)
    c.text(485, 799, "PAGE 2 OF 2", font="F2", size=9)

    # -------------------------------------------------------------------------
    # SCENARIO 4 CARD (Interruptions & Guards)
    # -------------------------------------------------------------------------
    card4_y = 618
    c.set_fill(0.985, 0.988, 0.995)
    c.set_stroke(0.82, 0.85, 0.90)
    c.set_line_width(0.75)
    c.rounded_rect(36, card4_y, 523, 154, r=5, fill=True, stroke=True)

    # Header in Card 4
    c.draw_badge(46, card4_y + 132, "PHASE 4", bg_rgb=(0.86, 0.15, 0.15), text_rgb=(1, 1, 1), font_size=8, padding_h=6, height=14)
    c.set_fill(0.06, 0.09, 0.16)
    c.text(98, card4_y + 134, "Real-World Interruptions, Guard Modals & Inactivity Daemon", font="F2", size=11)

    c.set_stroke(0.88, 0.90, 0.94)
    c.line(46, card4_y + 126, 545, card4_y + 126)

    # Steps in Card 4
    s4_y = card4_y + 108
    c.draw_step(48, s4_y, checkbox=True, bold_title="1. The 'Abandon Cart' Guard Test:",
                text="Add any product to your cart. Without checking out, click the red Logout button.")
    s4_y -= 14
    c.set_fill(0.25, 0.30, 0.38)
    c.text(64, s4_y, "--> Does the system warn you that you have unpurchased items? Click 'Keep Editing' first.", font="F3", size=8.5)

    s4_y -= 18
    c.draw_step(48, s4_y, checkbox=True, bold_title="2. Confirm Abandoning Cart:",
                text="Click Logout again. This time confirm cart abandonment. Does it clean up cleanly?")
    s4_y -= 18
    c.draw_step(48, s4_y, checkbox=True, bold_title="3. Inactivity Countdown Test:",
                text="Log back into your account. Step away or pause without touching mouse or keyboard.")
    s4_y -= 14
    c.set_fill(0.25, 0.30, 0.38)
    c.text(64, s4_y, "--> When 1 minute remains, an alert popup appears with a timer. Click 'Stay Logged In'.", font="F3", size=8.5)

    s4_y -= 18
    c.draw_step(48, s4_y, checkbox=True, bold_title="4. Clean Logout:",
                text="Click Logout to return to the public screen.")

    # -------------------------------------------------------------------------
    # SCENARIO 5 CARD (Admin Deposit & Storno Correction)
    # -------------------------------------------------------------------------
    card5_y = 444
    c.set_fill(0.985, 0.988, 0.995)
    c.set_stroke(0.82, 0.85, 0.90)
    c.set_line_width(0.75)
    c.rounded_rect(36, card5_y, 523, 160, r=5, fill=True, stroke=True)

    # Header in Card 5
    c.draw_badge(46, card5_y + 138, "PHASE 5", bg_rgb=(0.35, 0.15, 0.65), text_rgb=(1, 1, 1), font_size=8, padding_h=6, height=14)
    c.set_fill(0.06, 0.09, 0.16)
    c.text(98, card5_y + 140, "Admin Assisted Operations: Balance Recharge & Correction", font="F2", size=11)

    c.set_stroke(0.88, 0.90, 0.94)
    c.line(46, card5_y + 132, 545, card5_y + 132)

    # Steps in Card 5
    s5_y = card5_y + 114
    c.draw_step(48, s5_y, checkbox=True, bold_title="1. Hand Cash to Administrator:",
                text="Ask the store admin to record a deposit (e.g. R$ 50,00) on your account.")
    s5_y -= 18
    c.draw_step(48, s5_y, checkbox=True, bold_title="2. Verify Balance Update:",
                text="Log into the kiosk. Check your header balance: did it increase immediately?")
    s5_y -= 18
    c.draw_step(48, s5_y, checkbox=True, bold_title="3. Inspect Deposit Ledger Note:",
                text="Open Transaction History. Check the green 'ADMIN DEPOSIT' record and note.")
    s5_y -= 18
    c.draw_step(48, s5_y, checkbox=True, bold_title="4. Test Storno / Purchase Reversal:",
                text="Tell the admin you wish to cancel Scenario 2's purchase. Have admin storno it.")
    s5_y -= 18
    c.draw_step(48, s5_y, checkbox=True, bold_title="5. Verify Cancellation Receipt:",
                text="Check History: confirm the CANCELLATION entry references the original purchase date.")

    # -------------------------------------------------------------------------
    # TESTER FEEDBACK & OBSERVATION AREA
    # -------------------------------------------------------------------------
    feedback_y = 66
    c.set_fill(0.955, 0.965, 0.98)
    c.set_stroke(0.78, 0.82, 0.88)
    c.set_line_width(0.75)
    c.rounded_rect(36, feedback_y, 523, 364, r=5, fill=True, stroke=True)

    c.set_fill(0.06, 0.09, 0.16)
    c.text(48, feedback_y + 346, "Tester Reflections & Usability Feedback", font="F2", size=11)
    c.set_stroke(0.82, 0.86, 0.92)
    c.line(48, feedback_y + 340, 545, feedback_y + 340)

    # Question 1
    q_y = feedback_y + 322
    c.set_fill(0.12, 0.16, 0.24)
    c.text(48, q_y, "1. How easy was it to search items and enter/change weights (1,5 kg vs 750 g)?", font="F2", size=9)
    q_y -= 16
    c.draw_checkbox(58, q_y + 2, size=9); c.text(72, q_y, "Very intuitive", font="F1", size=8.5)
    c.draw_checkbox(170, q_y + 2, size=9); c.text(184, q_y, "Acceptable with minor friction", font="F1", size=8.5)
    c.draw_checkbox(330, q_y + 2, size=9); c.text(344, q_y, "Confusing / Difficult", font="F1", size=8.5)

    # Question 2
    q_y -= 22
    c.text(48, q_y, "2. Did the 'Balance Before -> After' flow and cart summary give you full clarity?", font="F2", size=9)
    q_y -= 16
    c.draw_checkbox(58, q_y + 2, size=9); c.text(72, q_y, "Completely clear", font="F1", size=8.5)
    c.draw_checkbox(170, q_y + 2, size=9); c.text(184, q_y, "Understood after looking twice", font="F1", size=8.5)
    c.draw_checkbox(330, q_y + 2, size=9); c.text(344, q_y, "Unclear / Ambiguous", font="F1", size=8.5)

    # Question 3
    q_y -= 22
    c.text(48, q_y, "3. How was account security (PIN, hiding balance on main screen, auto-logout)?", font="F2", size=9)
    q_y -= 16
    c.draw_checkbox(58, q_y + 2, size=9); c.text(72, q_y, "Felt very secure & private", font="F1", size=8.5)
    c.draw_checkbox(210, q_y + 2, size=9); c.text(224, q_y, "Adequate", font="F1", size=8.5)
    c.draw_checkbox(330, q_y + 2, size=9); c.text(344, q_y, "Annoying / Too intrusive", font="F1", size=8.5)

    # Ruled feedback lines
    q_y -= 26
    c.text(48, q_y, "4. What was the most confusing or delightful moment during the test?", font="F2", size=9)
    q_y -= 10
    for _ in range(5):
        q_y -= 20
        c.set_stroke(0.80, 0.84, 0.90)
        c.set_line_width(0.5)
        c.line(48, q_y, 545, q_y)

    # Question 5
    q_y -= 18
    c.text(48, q_y, "Overall Usability Grade:   [  ] A - Exceptional    [  ] B - Good    [  ] C - Needs Work    [  ] D - Poor", font="F2", size=8.8)

    # Page 2 Footer
    c.set_stroke(0.85, 0.88, 0.92)
    c.line(36, 50, 559, 50)
    c.set_fill(0.50, 0.55, 0.62)
    c.text(36, 38, "Lojinha Self-Service POS System  *  Usability Evaluation Mission Sheet", font="F1", size=8)
    c.text(470, 38, "Page 2 of 2  [Complete]", font="F2", size=8)

    c.compile()


if __name__ == "__main__":
    generate_mission_sheet()
