package khs.blog.service;

import jakarta.transaction.Transactional;
import khs.blog.domain.Article;
import khs.blog.dto.AddArticleRequest;
import khs.blog.dto.UpdateArticleRequest;
import khs.blog.repository.BlogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class BlogService {

    private final BlogRepository blogRepository;

    public void delete(long id) {
        Article article = blogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Not found: " + id));

        authorizeArticleAuthor(article);
        blogRepository.delete(article);
    }

    @Transactional
    public Article update(long id, UpdateArticleRequest request) {
        Article article = blogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Not found: " + id));

        authorizeArticleAuthor(article);
        article.update(request.getTitle(), request.getContent());

        return article;
    }

    private static void authorizeArticleAuthor(Article article) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null) {
            throw new IllegalArgumentException("Not authenticated");
        }
        
        String userName = authentication.getName();

        if (!article.getAuthor().equals(userName)) {
            throw new IllegalArgumentException("Not allowed");
        }
    }

    public Article save(AddArticleRequest request, String userName) {
        return blogRepository.save(request.toEntity(userName));
    }
    public List<Article> findAll(){
        return blogRepository.findAll();
    }

    public Article findById(Long id){
        return blogRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Not found: " + id));
    }

    public void deleteById(Long id){
        // Article이 존재하는지 먼저 확인
        if (!blogRepository.existsById(id)) {
            throw new IllegalArgumentException("Not found: " + id);
        }
        blogRepository.deleteById(id);
    }
}
