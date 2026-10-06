#ifndef SHA256_HPP
#define SHA256_HPP

#include <string>
#include <vector>
#include <cstdint>

class SHA256 {
public:
    SHA256();
    void update(const uint8_t* data, size_t length);
    void update(const std::string& data);
    std::string finalize();

    static std::string hash(const std::string& input);

private:
    void transform(const uint8_t* chunk);

    uint32_t state_[8];
    uint64_t bitlen_;
    uint8_t buffer_[64];
    size_t buffer_len_;
};

#endif // SHA256_HPP
