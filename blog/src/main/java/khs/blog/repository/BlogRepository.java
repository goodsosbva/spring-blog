package khs.blog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import khs.blog.domain.Article;

public interface BlogRepository extends JpaRepository<Article, Long> {
}
