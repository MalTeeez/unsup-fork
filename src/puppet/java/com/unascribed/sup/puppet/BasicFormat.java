package com.unascribed.sup.puppet;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BasicFormat {
	private static final Pattern PLACEHOLDER = Pattern.compile("%(?:([0-9+])\\$)?(.)");
	
	private interface Segment { void append(StringBuilder sb, Object[] args); }
	
	private static final class LiteralSegment implements Segment {
		private final String str;
		public LiteralSegment(String str) { this.str = str; }

		@Override
		public void append(StringBuilder sb, Object[] args) {
			sb.append(str);
		}
		
		@Override
		public String toString() {
			return "\""+str+"\"";
		}
	}
	
	private static final class PlaceholderSegment implements Segment {
		private final int idx;
		public PlaceholderSegment(int index) { this.idx = index; }

		@Override
		public void append(StringBuilder sb, Object[] args) {
			if (args.length > idx) {
				sb.append(args[idx]);
			}
		}
		
		@Override
		public String toString() {
			return "%"+(idx+1)+"$s";
		}
	}
	
	private static final class NewlineSegment implements Segment {
		public static final NewlineSegment INSTANCE = new NewlineSegment();
		private NewlineSegment() {}
		@Override
		public void append(StringBuilder sb, Object[] args) {
			sb.append("\n");
		}
		@Override
		public String toString() {
			return "%n";
		}
	}
	
	private static final ThreadLocal<StringBuilder> THREAD_BUILDER = ThreadLocal.withInitial(StringBuilder::new);
	
	private final List<Segment> segments = new ArrayList<>();
	
	private BasicFormat(Segment... segments) {
		for (Segment s : segments) {
			this.segments.add(s);
		}
	}
	
	private BasicFormat(String str) {
		Matcher m = PLACEHOLDER.matcher(str);
		int nextAutoIndex = 1;
		int last = 0;
		while (m.find()) {
			String head = str.substring(last, m.start());
			if (!head.isEmpty()) {
				segments.add(new LiteralSegment(head));
			}
			last = m.end();
			int idx;
			if (m.group(1) == null) {
				idx = 0;
			} else {
				idx = Integer.parseInt(m.group(1));
			}
			switch (m.group(2)) {
				case "n": {
					if (idx != 0) {
						throw new IllegalArgumentException("Newline placeholders cannot have an index: "+m.group(0));
					}
					segments.add(NewlineSegment.INSTANCE);
					break;
				}
				case "s": {
					if (idx == 0) {
						idx = nextAutoIndex;
						nextAutoIndex++;
					}
					idx--;
					segments.add(new PlaceholderSegment(idx));
					break;
				}
				default: {
					throw new IllegalArgumentException("Unsupported placeholder type: "+m.group(0));
				}
			}
		}
		String tail = str.substring(last);
		if (!tail.isEmpty()) {
			segments.add(new LiteralSegment(tail));
		}
	}
	
	public String format(Object... args) {
		StringBuilder sb = THREAD_BUILDER.get();
		sb.setLength(0);
		for (Segment s : segments) {
			s.append(sb, args);
		}
		return sb.toString();
	}
	
	public static BasicFormat parse(String str) {
		return new BasicFormat(str);
	}
	
	public static BasicFormat literal(String str) {
		return new BasicFormat(new LiteralSegment(str));
	}
	
}
