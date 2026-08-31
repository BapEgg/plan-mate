from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


WIDTH = 1280
HEIGHT = 720
FPS = 24
SLIDE_SECONDS = 4
FADE_SECONDS = 0.45


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    font_name = "malgunbd.ttf" if bold else "malgun.ttf"
    font_path = Path("C:/Windows/Fonts") / font_name
    return ImageFont.truetype(str(font_path), size=size)


def gradient_background() -> Image.Image:
    image = Image.new("RGB", (WIDTH, HEIGHT))
    pixels = image.load()
    for y in range(HEIGHT):
        for x in range(WIDTH):
            ratio = (x / WIDTH) * 0.65 + (y / HEIGHT) * 0.35
            pixels[x, y] = (
                int(17 + 25 * ratio),
                int(35 + 35 * ratio),
                int(76 + 85 * ratio),
            )
    return image


def title_card() -> Image.Image:
    image = gradient_background()
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((72, 72, 238, 112), radius=20, fill="#3967E8")
    draw.text((100, 80), "PRODUCT DEMO", font=font(18, True), fill="white")
    draw.text((76, 240), "PlanMate", font=font(72, True), fill="white")
    draw.text((76, 335), "AI 여행 일정 생성 흐름", font=font(42, True), fill="#E9F0FF")
    draw.text(
        (78, 420),
        "로그인 → 여행 조건 입력 → 비동기 일정 생성 → 날짜별 상세 확인",
        font=font(25),
        fill="#BFD0F5",
    )
    draw.text((78, 610), "실제 로컬 실행 화면 · 2026", font=font(18), fill="#91A9D8")
    return image


def render_slide(source: Path, eyebrow: str, title: str, description: str) -> Image.Image:
    screenshot = Image.open(source).convert("RGB")
    scale = max(WIDTH / screenshot.width, HEIGHT / screenshot.height)
    resized = screenshot.resize(
        (round(screenshot.width * scale), round(screenshot.height * scale)),
        Image.Resampling.LANCZOS,
    )
    left = max(0, (resized.width - WIDTH) // 2)
    top = max(0, (resized.height - HEIGHT) // 2)
    image = resized.crop((left, top, left + WIDTH, top + HEIGHT))

    overlay = Image.new("RGBA", image.size, (0, 0, 0, 0))
    overlay_draw = ImageDraw.Draw(overlay)
    overlay_draw.rectangle((0, 524, WIDTH, HEIGHT), fill=(8, 17, 39, 226))
    overlay_draw.rectangle((0, 524, 12, HEIGHT), fill=(65, 111, 238, 255))
    overlay_draw.text((52, 550), eyebrow, font=font(17, True), fill="#79A0FF")
    overlay_draw.text((52, 582), title, font=font(31, True), fill="white")
    overlay_draw.text((52, 635), description, font=font(20), fill="#CAD6F2")
    return Image.alpha_composite(image.convert("RGBA"), overlay).convert("RGB")


def frame_stream(slides: list[Image.Image]):
    hold_frames = int(SLIDE_SECONDS * FPS)
    fade_frames = int(FADE_SECONDS * FPS)
    for index, slide in enumerate(slides):
        for _ in range(hold_frames):
            yield slide.tobytes()
        if index == len(slides) - 1:
            continue
        next_slide = slides[index + 1]
        for step in range(1, fade_frames + 1):
            yield Image.blend(slide, next_slide, step / (fade_frames + 1)).tobytes()


def encode(ffmpeg: Path, slides: list[Image.Image], output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    command = [
        str(ffmpeg),
        "-y",
        "-f",
        "rawvideo",
        "-pixel_format",
        "rgb24",
        "-video_size",
        f"{WIDTH}x{HEIGHT}",
        "-framerate",
        str(FPS),
        "-i",
        "-",
        "-an",
        "-c:v",
        "libx264",
        "-preset",
        "medium",
        "-crf",
        "23",
        "-pix_fmt",
        "yuv420p",
        "-movflags",
        "+faststart",
        str(output),
    ]
    process = subprocess.Popen(command, stdin=subprocess.PIPE)
    assert process.stdin is not None
    try:
        for frame in frame_stream(slides):
            process.stdin.write(frame)
    finally:
        process.stdin.close()
    exit_code = process.wait()
    if exit_code != 0:
        raise RuntimeError(f"FFmpeg exited with code {exit_code}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Create the PlanMate workflow demo MP4.")
    parser.add_argument("--ffmpeg", type=Path, required=True)
    parser.add_argument("--assets", type=Path, default=Path("docs/assets/workflow"))
    args = parser.parse_args()

    slide_specs = [
        ("01-login.png", "STEP 1", "로그인", "인증을 마친 사용자가 PlanMate에 진입합니다."),
        ("02-trip-list.png", "STEP 2", "여행 대시보드", "내 여행을 확인하고 새 여행 생성을 시작합니다."),
        ("03-trip-create.png", "STEP 3", "목적지 선택", "실제 검색 결과에서 여행할 도시를 확정합니다."),
        ("03b-trip-conditions.png", "STEP 4", "여행 조건 입력 시작", "여행 기간부터 7단계 입력을 차례로 진행합니다."),
        ("04-trip-detail.png", "STEP 5", "비동기 일정 생성 완료", "후보 수집과 검증을 마친 일정의 저장 상태를 확인합니다."),
        ("05-day-itinerary.png", "STEP 6", "날짜별 일정 상세", "방문 순서·시간·장소 정보와 지도 위치를 확인합니다."),
        ("06-day-2.png", "STEP 7", "여행일 전환", "Day 탭으로 저장된 전체 여행 일정을 탐색합니다."),
    ]
    missing = [name for name, *_ in slide_specs if not (args.assets / name).exists()]
    if missing:
        raise FileNotFoundError(f"Missing screenshots: {', '.join(missing)}")

    slides = [title_card()]
    slides.extend(
        render_slide(args.assets / name, eyebrow, title, description)
        for name, eyebrow, title, description in slide_specs
    )
    output = args.assets / "planmate-workflow.mp4"
    encode(args.ffmpeg, slides, output)
    poster = render_slide(
        args.assets / "04-trip-detail.png",
        "PLANMATE WORKFLOW",
        "여행 조건에서 실행 가능한 일정까지",
        "약 35초 동안 실제 구현 흐름을 확인할 수 있습니다.",
    )
    poster.save(args.assets / "planmate-workflow-poster.png", optimize=True)
    print(output)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(exc, file=sys.stderr)
        raise
