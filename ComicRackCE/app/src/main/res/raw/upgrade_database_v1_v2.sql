ALTER TABLE books ADD StoryArc TEXT;
ALTER TABLE books ADD SeriesGroup TEXT;
ALTER TABLE books ADD Review TEXT;
ALTER TABLE books ADD MainCharacterOrTeam TEXT;
UPDATE books SET SourceModified = 0;
