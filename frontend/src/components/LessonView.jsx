function youtubeEmbed(url) {
  const m = String(url).match(/(?:youtube\.com\/(?:watch\?v=|embed\/)|youtu\.be\/)([\w-]{6,})/);
  return m ? `https://www.youtube.com/embed/${m[1]}` : null;
}

export default function LessonView({ lesson, index }) {
  const yt = lesson.videoUrl ? youtubeEmbed(lesson.videoUrl) : null;
  return (
    <div className="lesson">
      <div className="lesson-head">
        <span className="lesson-idx">{index + 1}</span>
        <div className="lesson-title">
          <strong>{lesson.title}</strong>
          {lesson.durationMinutes != null && <span className="muted small"> · {lesson.durationMinutes} min</span>}
        </div>
      </div>
      {lesson.description && <p className="muted small">{lesson.description}</p>}
      {lesson.videoUrl && (
        <div className="video-wrap">
          {yt ? (
            <iframe src={yt} title={lesson.title} allowFullScreen loading="lazy" />
          ) : (
            <video controls preload="metadata" src={lesson.videoUrl}>
              Your browser does not support embedded video. <a href={lesson.videoUrl}>Open video</a>.
            </video>
          )}
        </div>
      )}
      {lesson.documentUrl && (
        <a className="doc-link" href={lesson.documentUrl} target="_blank" rel="noreferrer">📄 Open lesson document</a>
      )}
    </div>
  );
}
