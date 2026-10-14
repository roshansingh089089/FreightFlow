export function RobotMascot() {
  return (
    <svg
      className="ff-robot"
      viewBox="0 0 96 96"
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <radialGradient id="ff-robot-shell" cx="31%" cy="19%" r="79%">
          <stop stopColor="#fff" />
          <stop offset=".57" stopColor="#dbece4" />
          <stop offset="1" stopColor="#8bb8a0" />
        </radialGradient>
        <linearGradient id="ff-robot-face" x1="0" y1="0" x2="1" y2="1">
          <stop stopColor="#343041" />
          <stop offset="1" stopColor="#172c31" />
        </linearGradient>
        <linearGradient id="ff-robot-body" x1="0" y1="0" x2="1" y2="1">
          <stop stopColor="#f9fffb" />
          <stop offset="1" stopColor="#9bc6ab" />
        </linearGradient>
      </defs>
      <ellipse
        className="ff-robot-shadow"
        cx="48"
        cy="87"
        rx="27"
        ry="5"
        fill="#132a26"
        opacity=".19"
      />
      <g className="ff-robot-body">
        <path
          d="M31 66q17-9 34 0l6 18H25z"
          fill="url(#ff-robot-body)"
          stroke="#5e9472"
          strokeWidth="2"
        />
        <path
          d="M37 75h22"
          stroke="#78a68b"
          strokeWidth="2"
          strokeLinecap="round"
        />
        <circle cx="48" cy="73" r="3" fill="#d49c54" />
      </g>
      <g className="ff-robot-head">
        <g className="ff-robot-antenna">
          <path
            d="M48 15V8"
            stroke="#5d8c75"
            strokeWidth="3"
            strokeLinecap="round"
          />
          <circle cx="48" cy="7" r="4" fill="#d9a55c" />
        </g>
        <rect
          x="10"
          y="27"
          width="10"
          height="24"
          rx="5"
          fill="#91bda2"
          stroke="#5b8d71"
          strokeWidth="2"
        />
        <rect
          x="76"
          y="27"
          width="10"
          height="24"
          rx="5"
          fill="#91bda2"
          stroke="#5b8d71"
          strokeWidth="2"
        />
        <rect
          x="15"
          y="13"
          width="66"
          height="56"
          rx="26"
          fill="url(#ff-robot-shell)"
          stroke="#5e9472"
          strokeWidth="2"
        />
        <path
          d="M23 30q5-11 17-12"
          fill="none"
          stroke="#fff"
          strokeOpacity=".9"
          strokeWidth="3"
          strokeLinecap="round"
        />
        <rect
          x="23"
          y="27"
          width="50"
          height="31"
          rx="14"
          fill="url(#ff-robot-face)"
        />
        <rect
          x="28"
          y="31"
          width="40"
          height="5"
          rx="2.5"
          fill="#fff"
          opacity=".09"
        />
        <g className="ff-robot-eyes">
          <ellipse cx="38" cy="42" rx="4" ry="5" fill="#a7e3ba" />
          <ellipse cx="58" cy="42" rx="4" ry="5" fill="#a7e3ba" />
        </g>
        <path
          d="M42 50q6 5 12 0"
          fill="none"
          stroke="#a7e3ba"
          strokeWidth="2.5"
          strokeLinecap="round"
        />
        <circle cx="18" cy="55" r="5" fill="#d9a55c" opacity=".72" />
        <circle cx="78" cy="55" r="5" fill="#d9a55c" opacity=".72" />
      </g>
    </svg>
  );
}
