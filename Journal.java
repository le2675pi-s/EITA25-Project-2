public record Journal(String id, String patient, String doctor, String nurse, String division, String info, String created) {
  	public Journal(String line) {
        this(
			split(line, 0),
			split(line, 1),
			split(line, 2),
			split(line, 3),
			split(line, 4),
			split(line, 5),
			split(line, 6)
		);
    }

    private static String split(String line, int index) {
        String[] parts = line.split(":", -1);
        return index < parts.length ? parts[index] : null;
    }
  
	@Override
	public String toString() {
		return id() + ":" + patient() + ":" + doctor() + ":" + nurse() + ":" + division() + ":" + info() + ":" + created();
	}
}
