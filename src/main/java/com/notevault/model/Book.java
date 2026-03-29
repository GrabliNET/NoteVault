package com.notevault.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @brief Книга — именованная коллекция заметок с фиксированным порядком.
 *
 * @details
 * Книга не является физическим файлом или папкой — это логическая группировка
 * заметок, хранящаяся в таблицах {@code books} и {@code book_notes} БД.
 *
 * ## Принцип работы
 * Заметки физически лежат в своих папках тегов и **не перемещаются** при
 * добавлении в книгу. При чтении книги сервисный слой делает SQL-запрос
 * с {@code ORDER BY order_index} и «склеивает» тела заметок в единый
 * Markdown-документ (без изменения файлов на диске).
 *
 * ## Front-matter заметки в книге
 * @code{.yaml}
 * ---
 * tags:
 *   - java
 * book:
 *   - java_guide
 * order: 2
 * ---
 * @endcode
 * Поле {@code book} содержит имя книги; {@code order} — позицию (с нуля).
 *
 * ## Экспорт
 * Книгу можно экспортировать целиком в PDF через
 * {@link com.notevault.service.PdfExporter#exportBook(Book, java.nio.file.Path)}.
 *
 * @see com.notevault.db.BookDAO
 * @see com.notevault.service.VaultService#getBookContent(Book)
 */
public class Book {

    /** @brief Первичный ключ в таблице {@code books}. */
    private long id;

    /**
     * @brief Уникальное имя книги.
     *
     * Используется как идентификатор в YAML front-matter заметок
     * (поле {@code book}). Ограничение {@code UNIQUE} в БД.
     */
    private String name;

    /** @brief Необязательное описание книги (отображается в начале при склейке). */
    private String description;

    /** @brief Дата создания книги. */
    private LocalDateTime createdAt;

    /**
     * @brief Заметки книги в порядке {@code order_index}.
     *
     * \note Поле заполняется только после явного запроса
     *       {@link com.notevault.service.VaultService#getNotesByBook(Book)}.
     */
    private List<Note> notes = new ArrayList<>();

    /** @brief Конструктор по умолчанию. */
    public Book() {}

    /**
     * @brief Создаёт книгу с заданным именем.
     * @param name Уникальное имя книги.
     */
    public Book(String name) {
        this.name = name;
        this.createdAt = LocalDateTime.now();
    }

    /** @return Первичный ключ. */
    public long getId() { return id; }
    /** @param id Первичный ключ. */
    public void setId(long id) { this.id = id; }

    /** @return Имя книги. */
    public String getName() { return name; }
    /** @param name Имя книги. */
    public void setName(String name) { this.name = name; }

    /** @return Описание книги или {@code null}. */
    public String getDescription() { return description; }
    /** @param description Описание. */
    public void setDescription(String description) { this.description = description; }

    /** @return Дата создания. */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /** @param createdAt Дата создания. */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /** @return Список заметок (может быть пустым если не загружен). */
    public List<Note> getNotes() { return notes; }
    /** @param notes Список заметок. */
    public void setNotes(List<Note> notes) { this.notes = notes; }

    /** @return Имя книги. */
    @Override public String toString() { return name; }
}