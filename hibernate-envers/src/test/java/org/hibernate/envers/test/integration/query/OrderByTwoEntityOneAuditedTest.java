/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.envers.test.integration.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.OrderBy;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.hibernate.envers.test.BaseEnversJPAFunctionalTestCase;
import org.junit.Test;

import org.hibernate.testing.TestForIssue;

import static org.hibernate.testing.transaction.TransactionUtil.doInJPA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test the use of the {@link OrderBy} annotation on a many-to-many collection
 * where the two entities are audited but the association is not.
 *
 * The double audited entity but no association audited mapping invokes the use
 * of the TwoEntityOneAuditedGenerator which we want to verify orders the
 * collection results properly.
 *
 * @author Chris Cranford
 */
@TestForIssue(jiraKey = "HHH-12992")
public class OrderByTwoEntityOneAuditedTest extends BaseEnversJPAFunctionalTestCase {
	@Entity(name = "Parent")
	@Audited
	public static class Parent {
		@Id
		@GeneratedValue
		private Integer id;

		@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
		@ManyToMany(mappedBy = "parents", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
		@OrderBy("index, index2 desc")
		@Fetch(FetchMode.SELECT)
		@BatchSize(size = 100)
		private List<Child> children = new ArrayList<>();

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public List<Child> getChildren() {
			return children;
		}

		public void setChildren(List<Child> children) {
			this.children = children;
		}
	}

	@Entity(name = "Child")
	@Audited
	public static class Child {
		@Id
		private Integer id;

		private Integer index;
		private Integer index2;

		@ManyToMany
		private List<Parent> parents = new ArrayList<>();

		public Child() {

		}

		public Child(Integer id, Integer index) {
			this.id = id;
			this.index = index;
		}

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public Integer getIndex() {
			return index;
		}

		public void setIndex(Integer index) {
			this.index = index;
		}

		public Integer getIndex2() {
			return index2;
		}

		public void setIndex2(Integer index2) {
			this.index2 = index2;
		}

		public List<Parent> getParents() {
			return parents;
		}

		public void setParents(List<Parent> parents) {
			this.parents = parents;
		}

		@Override
		public boolean equals(Object o) {
			if ( this == o ) {
				return true;
			}
			if ( o == null || getClass() != o.getClass() ) {
				return false;
			}
			Child child = (Child) o;
			return Objects.equals( id, child.id ) &&
					Objects.equals( index, child.index );
		}

		@Override
		public int hashCode() {
			return Objects.hash( id, index );
		}

		@Override
		public String toString() {
			return "Child{" +
					"id=" + id +
					", index=" + index +
					'}';
		}
	}

	@Override
	protected Class<?>[] getAnnotatedClasses() {
		return new Class<?>[] { Parent.class, Child.class };
	}

	private Integer parentId;

	@Test
	public void initData() {
		// Rev 1
		this.parentId = doInJPA( this::entityManagerFactory, entityManager -> {
			final Parent parent = new Parent();

			final Child child1 = new Child();
			child1.setId( 1 );
			child1.setIndex( 1 );
			child1.setIndex2( 1 );
			child1.getParents().add( parent );
			parent.getChildren().add( child1 );

			final Child child2 = new Child();
			child2.setId( 2 );
			child2.setIndex( 2 );
			child2.setIndex2( 2 );
			child2.getParents().add( parent );
			parent.getChildren().add( child2 );

			entityManager.persist( parent );

			return parent.getId();
		} );

		// Rev 2
		doInJPA( this::entityManagerFactory, entityManager -> {
			final Parent parent = entityManager.find( Parent.class, parentId );

			final Child child = new Child();
			child.setId( 3 );
			child.setIndex( 3 );
			child.setIndex2( 3 );
			child.getParents().add( parent );
			parent.getChildren().add( child );

			entityManager.merge( parent );
		} );

		// Rev 3
		doInJPA( this::entityManagerFactory, entityManager -> {
			final Parent parent = entityManager.find( Parent.class, parentId );
			parent.getChildren().removeIf( c -> {
				if ( c.getIndex() == 2 ) {
					c.getParents().remove( parent );
					return true;
				}
				return false;
			} );
			entityManager.merge( parent );
		} );

		// Rev 4
		doInJPA( this::entityManagerFactory, entityManager -> {
			final Parent parent = entityManager.find( Parent.class, parentId );
			parent.getChildren().forEach( c -> c.getParents().clear() );
			parent.getChildren().clear();
			entityManager.merge( parent );
		} );
	}

	@Test
	public void testRevisionCounts() {
		assertEquals( Arrays.asList( 1, 2, 3, 4 ), getAuditReader().getRevisions( Parent.class, this.parentId ) );
		assertEquals( Arrays.asList( 1, 4 ), getAuditReader().getRevisions( Child.class, 1 ) );
		assertEquals( Arrays.asList( 1, 3 ), getAuditReader().getRevisions( Child.class, 2 ) );
		assertEquals( Arrays.asList( 2, 4 ), getAuditReader().getRevisions( Child.class, 3 ) );
	}

	@Test
	public void testRevision1History() {
		final Parent parent = getAuditReader().find( Parent.class, this.parentId, 1 );
		assertNotNull( parent );
		assertTrue( !parent.getChildren().isEmpty() );
		assertEquals( 2, parent.getChildren().size() );
		assertEquals( Arrays.asList( new Child( 1, 1 ), new Child( 2, 2 ) ), parent.getChildren() );
	}

	@Test
	public void testRevision2History() {
		final Parent parent = getAuditReader().find( Parent.class, this.parentId, 2 );
		assertNotNull( parent );
		assertTrue( !parent.getChildren().isEmpty() );
		assertEquals( 3, parent.getChildren().size() );
		assertEquals( Arrays.asList( new Child( 1, 1 ), new Child( 2, 2 ), new Child( 3, 3 ) ), parent.getChildren() );
	}

	@Test
	public void testRevision3History() {
		final Parent parent = getAuditReader().find( Parent.class, this.parentId, 3 );
		assertNotNull( parent );
		assertTrue( !parent.getChildren().isEmpty() );
		assertEquals( 2, parent.getChildren().size() );
		assertEquals( Arrays.asList( new Child( 1, 1 ), new Child( 3, 3 ) ), parent.getChildren() );
	}

	@Test
	public void testRevision4History() {
		final Parent parent = getAuditReader().find( Parent.class, this.parentId, 4 );
		assertNotNull( parent );
		assertTrue( parent.getChildren().isEmpty() );
	}
}