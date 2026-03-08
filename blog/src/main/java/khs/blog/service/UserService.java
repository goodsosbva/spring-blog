package khs.blog.service;

import khs.blog.domain.User;
import khs.blog.dto.AddUserRequest;
import khs.blog.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class UserService {
    private final UserDetailService userDetailService;
    private  final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final UserRepository userRepository;

    public Long save(AddUserRequest dto) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        return userRepository.save(User.builder()
                .email(dto.getEmail())
                .password(encoder.encode(dto.getPassword()))
                .build()).getId();
    }

    public User findById(Long id){
        return userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Unexpected user"));
    }

    public User findByEmail(String email){
        return userRepository.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("Unexpected user"));
    }
}
