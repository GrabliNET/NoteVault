package com.notevault.model;

/**
 * @brief Тег (ярлык) для быстрой фильтрации заметок.
 *
 * @details
 * Теги хранятся в таблице {@code tags} БД с ограничением {@code UNIQUE COLLATE NOCASE}
 * (регистронезависимая уникальность). Связь с заметками реализована через
 * промежуточную таблицу {@code note_tags} (many-to-many).
 *
 * В YAML front-matter заметки теги записываются как список:
 * @code{.yaml}
 * tags:
 *   - java
 *   - tutorial
 * @endcode
 *
 * Физически заметки с одним тегом размещаются в папке с именем тега,
 * что позволяет ориентироваться в хранилище без приложения.
 *
 * @see com.notevault.db.TagDAO
 * @see com.notevault.util.VaultLayout
 */
public class Tag {

    /** @brief Первичный ключ в таблице {@code tags}. */
    private long id;

    /**
     * @brief Имя тега (без символа {@code #}).
     *
     * Хранится в нижнем регистре по соглашению; сравнение в БД регистронезависимо.
     */
    private String name;

    /**
     * @brief Количество заметок с данным тегом.
     *
     * \note Вспомогательное поле — заполняется при запросе
     *       {@link com.notevault.db.TagDAO#findAll()} из агрегирующего SQL.
     *       Не хранится в БД отдельной колонкой.
     */
    private int noteCount;

    /** @brief Конструктор по умолчанию. */
    public Tag() {}

    /**
     * @brief Создаёт тег с заданным именем.
     * @param name Имя тега (без {@code #}).
     */
    public Tag(String name) { this.name = name; }

    /** @return Первичный ключ. */
    public long getId() { return id; }
    /** @param id Первичный ключ. */
    public void setId(long id) { this.id = id; }

    /** @return Имя тега. */
    public String getName() { return name; }
    /** @param name Имя тега. */
    public void setName(String name) { this.name = name; }

    /** @return Число заметок с этим тегом (денормализованное поле). */
    public int getNoteCount() { return noteCount; }
    /** @param noteCount Число заметок. */
    public void setNoteCount(int noteCount) { this.noteCount = noteCount; }

    /** @return Имя тега. */
    @Override public String toString() { return name; }

    /**
     * @brief Два тега равны, если совпадают их имена (регистронезависимо).
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Tag tag)) return false;
        return name != null && name.equalsIgnoreCase(tag.name);
    }

    @Override
    public int hashCode() {
        return name != null ? name.toLowerCase().hashCode() : 0;
    }
}