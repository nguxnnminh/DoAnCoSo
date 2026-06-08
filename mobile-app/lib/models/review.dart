class Review {
  final int id;
  final double rating;
  final String comment;
  final String authorName;
  final List<String> imageUrls;
  final String createdAt;

  const Review({
    required this.id,
    required this.rating,
    required this.comment,
    required this.authorName,
    required this.imageUrls,
    required this.createdAt,
  });

  factory Review.fromJson(Map<String, dynamic> j) => Review(
        id: j['id'] as int,
        rating: (j['rating'] as num).toDouble(),
        comment: j['comment'] as String? ?? '',
        authorName: j['authorName'] as String? ?? 'Anonymous',
        imageUrls: (j['imageUrls'] as List<dynamic>?)
                ?.map((e) => e as String)
                .toList() ??
            [],
        createdAt: j['createdAt'] as String? ?? '',
      );
}

class ProductReviews {
  final double average;
  final int count;
  final List<Review> reviews;

  const ProductReviews({
    required this.average,
    required this.count,
    required this.reviews,
  });

  factory ProductReviews.fromJson(Map<String, dynamic> j) => ProductReviews(
        average: (j['average'] as num).toDouble(),
        count: j['count'] as int,
        reviews: (j['reviews'] as List<dynamic>)
            .map((e) => Review.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}
