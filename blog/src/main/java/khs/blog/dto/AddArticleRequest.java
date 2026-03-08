package khs.blog.dto;


import khs.blog.domain.Article;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static khs.blog.domain.Article.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class AddArticleRequest {
    private String title;

    private String content;

    public Article toEntity(String author) {
        return builder()
                .title(title)
                .content(content)
                .author(author)
                .build();
    }
}